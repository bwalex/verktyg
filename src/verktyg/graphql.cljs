(ns verktyg.graphql
  (:require
    [clojure.string :as str]
    [goog.object]
    [camel-snake-kebab.core :refer [->PascalCaseString]]))

(defn err-coll? [v]
  (let [vs (:errors v)]
    (and (coll? vs)
         (contains? (first vs) :message))))

(defn err-coll [v]
  (:errors v))

(defn err-coll->str [v]
  (str/join ", " (map :message (err-coll v))))

(defn errs->str [v]
  (let [res (if (map? v)
              v
              (js->clj (.-result v) :keywordize-keys true))]
    (cond
      (err-coll? res)           (err-coll->str res)
      (seq (.-graphQLErrors v)) (->> v .-graphQLErrors (map #(.-message %)) (str/join ", "))
      (.-networkError v)        (errs->str (.-networkError v))
      (.-message v)             (.-message v)
      :else                     v)))

(def ^:dynamic *client* ^{:doc "Instance of `ApolloClient`"}
  (atom {}))

(defn set-client!
  ([c] (set-client! c :default))
  ([c ep]
   (swap! *client* assoc ep c)))

(comment
  (:require
    [apollo-client :refer [ApolloClient]]
    [apollo-link-http :refer [HttpLink]]
    [apollo-link-error :refer [onError]]
    [apollo-cache-inmemory :refer [InMemoryCache]])

  (defn link-error-fn
    "This link error handler runs after each GraphQL response from the backend.
    If the backend returns a 401 response, a `:logged-out` event is dispatched."
    [net-err]
    (let [status-code (.. net-err -networkError -statusCode)
          response (.. net-err -networkError -response)]
      (when (= status-code 401)
        (re-frame/dispatch [:logged-out]))))

  (def apollo-net-intf
    (let [http-link (HttpLink. #js {:uri (str config/backend-url "/graphql")
                                    :credentials "include"})
          err-link (onError link-error-fn)]
      (.concat err-link http-link)))

  (verktyg.graphql/set-client!
    (ApolloClient. #js {:link apollo-net-intf
                        :cache (InMemoryCache.)})))

(defn reset-apollo-cache!
  "Reset the Apollo Cache. If the store is reset whilst a query is in flight,
  the query will fail with an error."
  ([]   (reset-apollo-cache! :default))
  ([ep]
   (.resetStore (get @*client* ep))))

(def ^:dynamic *gql-map* ^{:doc "A hash map of GraphQL queries and mutations as returned by `gql-builder`"}
  (atom {:fragment []
         :query []
         :mutation []}))

(defn set-gql-map!
  "Set the map of available fragments/queries/mutations. Can either be a map
  as returned by `graphql-builder` or a JS object as generated by `gulp-gql`."
  [gql-map]
  (let [x (if (map? gql-map)
            gql-map
            {:fragment (.. gql-map -fragment)
             :query    (.. gql-map -query)
             :mutation (.. gql-map -mutation)})]
    (reset! *gql-map* x)))

(defn gql-map-get [type name]
  (goog.object/get (get @*gql-map* type) name))

(defn gql-doc [query-index]
  (let [type  (first query-index)
        name  (->PascalCaseString (second query-index))
        op    ^js (gql-map-get type name)
        frags (map (partial gql-map-get :fragment) (.-deps op))]
    #js {:kind "Document"
         :definitions (into-array (cons (.-op op) frags))}))

(defn get-query
  "Given a query index such as `[:query :foo]` and query variables as a map,
  return a vector containing both the object returned by `graphql-builder`
  for the specified query, and a JS-compatible GraphQL `DocumentNode`.
  The object returned by `graphql` builder has the following shape:
  {:graphql {:query \"GraphQL Query string\"
             :variables {...}
             :operationName \"...\"}
   :unpack (fn [data])}"
  ([query-index] (get-query query-index {}))
  ([query-index vars]
   {:pre [(vector? query-index)
          (map? vars)]}
   (let [doc (gql-doc query-index)]
     [{:graphql {:query ""
                 :variables vars
                 :operationName (str query-index)}
       :unpack identity}
      doc])))

;; -- Mutations ---------------------------------------------------------------

(defn refetch-query->js
  "Convert a vector containing a query index and a map of variables into a
  JavaScript object understood by `ApolloClient`'s `refetchQueries` argument."
  [[query-index vars]]
  {:pre [(vector query-index)
         (map? vars)]}
  (let [[_ qdoc] (get-query query-index vars)]
    #js {:query qdoc
         :variables (clj->js vars)}))

(defn try-unpack-single [data]
  (let [vs (vals data)]
    (if (= (count vs) 1)
      (first vs)
      vs)))

(defn invalidate-queries
  ([qs] (invalidate-queries qs :default))
  ([qs ep]
   (doseq [q qs]
     (.. (get @*client* ep) -queryManager (refetchQueryByName q)))))

(defn mutate!
  "Execute a GraphQL mutation using `ApolloClient`'s `mutate` method. The
  `refetch-queries` argument is a list of `[query-index vars]` vectors for
  queries that should be refetched after the mutation. See `ApolloClient`'s
  `refetchQueries for more information.
  If the mutation is successful, the `on-done` callback will be called with
  the result of the mutation.
  On error, the `on-failure` callback is called with a string representation
  of the error.
  After both success and failure callbacks, the on-done callback is called
  without any arguments."
  [{:keys [query-index endpoint vars on-success on-failure on-done refetch-queries inval-queries unpack-error-fn]
    :or {endpoint :default
         vars {}
         refetch-queries []
         inval-queries []
         on-done (constantly nil)
         on-success (constantly nil)
         on-failure (constantly nil)
         unpack-error-fn errs->str}}]
  {:pre [(vector query-index)
         (map? vars)]}
  (let [[gq qdoc] (get-query query-index vars)
        unpack-fn (comp try-unpack-single (:unpack gq) :data)
        client (get @*client* endpoint)
        rqs (into [] (map refetch-query->js refetch-queries))
        q (.mutate client #js {:mutation qdoc
                               :refetchQueries (clj->js rqs)
                               :variables (clj->js vars)})]
    (-> q
        (.then  (fn [data]
                  (invalidate-queries inval-queries endpoint)
                  (on-success (-> data
                                  (js->clj :keywordize-keys true)
                                  unpack-fn))
                  (on-done)))
        (.catch (fn [err]
                  (on-failure (unpack-error-fn err))
                  (on-done))))))

;; -- Watch query -------------------------------------------------------------

(defn sub-next-handler [gq on-next data]
  (let [unpack-fn (comp (:unpack gq) :data)
        raw-data (js->clj data :keywordize-keys true)
        net-status (:networkStatus raw-data)
        d (unpack-fn raw-data)]
    (on-next d net-status)))

(defn sub-error-handler [gq on-failure unpack-error-fn err]
  (let [x (unpack-error-fn err)]
    (on-failure x)))

(defn watch-query
  "Subscribe to a GraphQL query with the provided variables. The return value
  is the raw subscription (i.e. Apollo `ObservableQuery`). To dispose of the
  subscription, call the `.unsubscribe` method on it: `(.unsubscribe sub)`.

  The `on-next` callback will be called once initially, and on every update
  of the underlying query, with two arguments:
   - the query result as a map
   - the Apollo network status of the request as an int. Values: (loading = 1,
     setVariables = 2, fetchMore = 3, refetch = 4, poll = 6, ready = 7).

  On error, the `on-failure` callback is called with a string representation
  of the error."
  [{:keys [query-index endpoint vars on-next on-failure fetch-policy unpack-error-fn]
    :or {endpoint :default
         vars {}
         on-next (constantly nil)
         on-failure (constantly nil)
         fetch-policy "cache-and-network"
         unpack-error-fn errs->str}}]
  (let [[gq qdoc] (get-query query-index vars)
        unpack-fn (comp (:unpack gq) :data)
        client (get @*client* endpoint)
        q  (.watchQuery client #js {:query qdoc
                                    :notifyOnNetworkStatusChange true
                                    :fetchPolicy fetch-policy
                                    :variables (clj->js vars)})
        sub (.subscribe q #js {:next  (partial sub-next-handler gq on-next)
                               :error (partial sub-error-handler gq on-failure unpack-error-fn)})]
    (sub-next-handler gq on-next (.currentResult q))
    sub))
