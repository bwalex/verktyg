(ns verktyg.graphql.re-frame
  (:require
    [re-frame.core :as re-frame]
    [reagent.core :as r]
    [reagent.ratom]
    [verktyg.graphql :as gql]))

;; ::gql
;;
;; Subscribe to a GraphQL query with the provided variables. The result is a
;; the query result as a map, with the following additional keys:
;;
;;  - `:gql/error`: An error message string if an error occurs.
;;  - `:gql/network-status`: The Apollo network status of the request as an
;;    int. Values: (loading = 1, setVariables = 2, fetchMore = 3, refetch = 4,
;;    poll = 6, ready = 7).
;;
;; Usage: [::gql [:query :some-query] {:some vars}]
(re-frame/reg-sub-raw
  ::gql
  (fn [_ [_ query-index vars {:keys [fetch-policy endpoint]
                              :or {fetch-policy "cache-and-network"
                                   endpoint :default}}]]
    {:pre [(vector? query-index)
           (map? vars)]}
    (let [state (r/atom {})
          sub (gql/watch-query {:query-index query-index
                                :endpoint endpoint
                                :vars vars
                                :fetch-policy fetch-policy
                                :on-next #(reset! state (merge %1 {:gql/network-status %2}))
                                :on-failure #(reset! state {:gql/error %})})]
      (reagent.ratom/make-reaction
        (fn [] @state)
        :on-dispose (fn []
                      (when sub (.unsubscribe sub)))))))

;; ::gql-reset-cache
;;
;; Reset the Apollo Cache. This effect should not be used as part of a
;; `dispatch-sync`'d event from within a GraphQL query/mutation.
(re-frame/reg-fx
  ::gql-reset-cache
  (fn [_]
    (gql/reset-apollo-cache!)))

;; ::gql-query-fx
;;
;; Perform a GraphQL query for the query under `query` with the variables in
;; `vars`.
;; If the query succeeds, `dispatch` the `on-success` event, conjoined with
;; the query result (after applying `extract-fn` to it, which defaults to
;; `identity`).
;; If the query fails, `dispatch` the `on-failure` event, conjoined with an
;; error string.
(re-frame/reg-fx
  ::gql-query-fx
  (fn [{:keys [query vars extract-fn on-success on-failure unpack-error-fn endpoint]
        :or {extract-fn identity
             unpack-error-fn gql/errs->str
             endpoint :default
             vars {}}}]
    (let [[gq qdoc] (gql/get-query query vars)
          unpack-fn (comp extract-fn
                          :data
                          (:unpack gq)
                          #(js->clj % :keywordize-keys true))
          client (get @gql/*client* endpoint)
          p (.query client (clj->js {:query qdoc
                                     :fetchPolicy "network-only"}))]
      (-> p
          (.then  (fn [resp]
                    (re-frame/dispatch (conj on-success (unpack-fn resp)))))
          (.catch (fn [e]
                    (re-frame/dispatch (conj on-failure (unpack-error-fn e)))))))))
