(ns verktyg.re-frame
  (:require
    [clojure.string :as str]
    [reagent.core :as r]
    [reagent.ratom]
    [re-frame.core :as re-frame]
    [verktyg.core :as vc]))

;; XXX: split things out into the function and then the re-frame registration

;; -- Media Query -------------------------------------------------------------

;; ::media-query
;;
;; Subscribe to a media query. Result is a boolean true/false result of
;; whether the media query currently matches.
;;
;; Usage: [::media-query ["(min-width: 992px)"]]
(re-frame/reg-sub-raw
  ::media-query
  (fn [_ [_ mqstr]]
    (let [state (r/atom false)
          listen-fn #(reset! state (.-matches %))
          mql (.matchMedia js/window mqstr)]
      (.addListener mql listen-fn)
      (listen-fn mql)
      (reagent.ratom/make-reaction
        (fn [] @state)
        :on-dispose (fn []
                      (when mql (.removeListener mql listen-fn)))))))


;; -- Window size -------------------------------------------------------------

;; ::window-size
;;
;; Subscribe to the window size. Result is a map of `{:height, :width}`.
;;
;; Usage: [::window-size]]
(re-frame/reg-sub-raw
  ::window-size
  (fn [_ _]
    (let [state (r/atom {:width nil, :height nil})
          listen-fn (fn []
                      (reset! state {:width  (.-innerWidth  js/window)
                                     :height (.-innerHeight js/window)}))]
      (.addEventListener js/window "resize" listen-fn)
      (listen-fn)
      (reagent.ratom/make-reaction
        (fn [] @state)
        :on-dispose (fn []
                      (.removeEventListener js/window "resize" listen-fn))))))


;; -- Interceptors ------------------------------------------------------------

;; interceptor to persist to localstorage
(defn ->local-store
  "An interceptor whose `:after` persists the keys `ks` of the app-db
  to the browser local store under those same keys."
  [& ks]
  (re-frame/after
    (fn [db]
      (doseq [k ks]
        (vc/set-local-store k (get db k))))))

;; -- Co-Effects --------------------------------------------------------------

;; ::local-store
;;
;; Adds to coeffects the contents of the browser local store for the
;; keys `ks`, under the key `:local-store`.
(re-frame/reg-cofx
  ::local-store
  (fn [cofx & ks]
    (assoc cofx
           :local-store
           (into {} (map #(hash-map % (vc/get-local-store %)) ks)))))


;; -- Effects -----------------------------------------------------------------

;; ::json-http-fx
;;
;; Perform an HTTP access to `url` using the specified `method`, assuming
;; JSON encoding of the data and the result.
;; If the access succeeds, `dispatch` the `on-success` event, conjoined with
;; the result (after applying `extract-fn` to it, which defaults to
;; `identity`).
;; If the access fails or the JSON decoding fails, `dispatch` the `on-failure`
;; event, conjoined with an error string.
(re-frame/reg-fx
  ::json-http-fx
  (fn [{:keys [url method data extract-fn err-extract-fn on-success on-failure]
        :or {method "POST"
             data nil
             extract-fn identity
             err-extract-fn identity}}]
    {:pre [(vector? on-success)
           (vector? on-failure)]}
    (let [body (when data (vc/->json data))
          p (vc/fetch url
                      {:method (-> method name str/upper-case)
                       :credentials "include"
                       :headers {"Accept" "application/json"
                                 "Content-Type" "application/json"}
                       :body body})]
      (-> p
          (.then #(.json %))
          (.then #(js->clj % :keywordize-keys true))
          (.then
            (fn [d]
              (if (seq (:errors d))
                (re-frame/dispatch (conj on-failure (err-extract-fn d)))
                (re-frame/dispatch (conj on-success (extract-fn d))))))
          (.catch
            (fn [e]
              (re-frame/dispatch (conj on-failure (err-extract-fn e)))))))))


;; ::transit-http-fx
;;
;; Perform an HTTP access to `url` using the specified `method`, assuming
;; transit encoding of the data and the result.
;; If the access succeeds, `dispatch` the `on-success` event, conjoined with
;; the result (after applying `extract-fn` to it, which defaults to
;; `identity`).
;; If the access fails or the decoding fails, `dispatch` the `on-failure`
;; event, conjoined with an error string.
(re-frame/reg-fx
  ::transit-http-fx
  (fn [{:keys [url method data extract-fn err-extract-fn on-success on-failure]
        :or {method "POST"
             data nil
             extract-fn identity
             err-extract-fn identity}}]
    {:pre [(vector? on-success)
           (vector? on-failure)]}
    (let [body (when data (vc/->transit data))
          p (vc/fetch url
                      {:method (-> method name str/upper-case)
                       :credentials "include"
                       :headers {"Accept" "application/transit+json"
                                 "Content-Type" "application/transit+json"}
                       :body body})]
      (-> p
          (.then #(.text %))
          (.then #(vc/transit->clj %))
          (.then
            (fn [d]
              (if (seq (:errors d))
                (re-frame/dispatch (conj on-failure (err-extract-fn d)))
                (re-frame/dispatch (conj on-success (extract-fn d))))))
          (.catch
            (fn [e]
              (re-frame/dispatch (conj on-failure (err-extract-fn e)))))))))
