(ns verktyg.history
  (:require [clojure.string :as str]
            [goog.events])
  (:import
    [goog.history Html5History EventType]))

;; adapted from circleci/frontend
(def ^{:doc "Custom token transformer that doesn't preserve the query string"}
  token-transformer
  (let [transformer (js/Object.)]
    (set! (.-retrieveToken transformer)
          (fn [path-prefix location]
            (subs (.-pathname location) (count path-prefix))))
    (set! (.-createUrl transformer)
          (fn [token path-prefix location]
            (str path-prefix token)))
    transformer))

;; from circleci/frontend
(defn set-current-token!
  "Lets us keep track of the history state, so that we don't dispatch twice on the same URL"
  [^goog history-imp]
  (set! (.-_current_token history-imp) (.getToken history-imp)))

(defn current-token
  [^goog history-imp]
  (let [token (.getToken history-imp)
        token (if (str/starts-with? token "/") token (str "/" token))]
    token))

(defn disable-erroneous-popstate!
  "Stops the browser's popstate from triggering NAVIGATION events unless the url has really
   changed. Fixes duplicate dispatch in Safari and the build machines."
  [^goog history-imp]
  ;; get this history instance's version of window, might make for easier testing later
  (let [window (.-window_ history-imp)]
    (goog.events/removeAll window goog.events.EventType.POPSTATE)
    (goog.events/listen window goog.events.EventType.POPSTATE
                        #(if (= (.getToken history-imp)
                                (.-_current_token history-imp))
                           (println (str "Ignoring duplicate dispatch event to" (.getToken history-imp)))
                           (.onHistoryEvent_ history-imp %)))))


(defn make-history
  "Initialize an `Html5History` instance with our custom `token-transformer`."
  ([]              (make-history true))
  ([use-fragment?] (make-history use-fragment? "/"))
  ([use-fragment? path-prefix]
   (doto (Html5History. js/window token-transformer)
     (.setUseFragment use-fragment?)
     (.setPathPrefix path-prefix))))

(defn setup-subscription!
  "Subscribes `nav-fn` to navigation events on `history`. Also takes care of
  ensuring the token/url passed to `nav-fn` is always prefixed with a `/` and
  remembers the current token to prevent duplicate navigation events when
  popping a state. See `disable-erroneous-popstate!` for more information."
  [^goog history nav-fn]
  (goog.events/listen history EventType.NAVIGATE
                      (fn [^goog v]
                        (let [token   (.-token v)
                              token   (if (str/starts-with? token "/") token (str "/" token))
                              is-nav? (.-isNavigation v)]
                          (set-current-token! history)
                          (nav-fn token is-nav?)))))

(defn start-history! [^goog history nav-fn]
  "Enables the history handling on the `history` Html5History instance and
  subscribes `nav-fn` as the navigation handler function.
  Also sets up workarounds - see `disable-erroneous-popstate!` for more
  details"
  (doto history
    (setup-subscription! nav-fn)
    (set-current-token!) ; Stop Safari from double-dispatching
    (disable-erroneous-popstate!) ; Stop Safari from double-dispatching
    (.setEnabled true)))

(defn history-push
  "Navigate to `url`, pushing a new history entry. The `title` is ignored by
  most browsers."
  ([^goog history url] (history-push history url nil))
  ([^goog history url title]
   (let [url (str/replace url #"^#" "")]
     (.setToken history url title))))

(defn history-replace
  "Navigate to `url`, *without* pushing a new history entry (overriding the
  current one). The `title` is ignored by most browsers."
  ([^goog history url] (history-replace history url nil))
  ([^goog history url title]
   (let [url (str/replace url #"^#" "")]
     (.replaceToken history url title))))
