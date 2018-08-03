(ns verktyg.nav.re-frame
  (:require
    [re-frame.core :as re-frame]
    [verktyg.nav :as nav]))

;; ::nav-push-fx
;;
;; Navigate to the given `route` using the specified route `params`. If
;; `replace` is true, replace the current history entry instead.
(re-frame/reg-fx
  ::nav-push-fx
  (fn [{:keys [route params replace]}]
    (let [url (nav/url-to route params)
          f (if replace nav/history-replace nav/history-push)]
      (f url))))


(re-frame/reg-event-fx
  ::update-location
  (fn [{:keys [db]} [_ path route fragments params]]
    {:db (assoc db ::location {:path path
                               :route route
                               :fragments fragments
                               :params params})}))

(re-frame/reg-event-fx
  ::nav-push
  (fn [{:keys [db]} [_ route route-params]]
    (let [params    (get-in db [::location :params] {})
          fragments (get-in db [::location :fragments] #{})
          route     (nav/translate-route fragments route)]
      {::nav-push-fx {:route route
                      :params (merge params route-params)
                      :replace false}})))

(re-frame/reg-sub
  ::location
  ::location)

(defn dispatch
  [path route fragments params]
  (re-frame/dispatch [::update-location path route fragments params]))
