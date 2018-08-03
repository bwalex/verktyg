(ns verktyg.nav
  (:require
    [reitit.core :as reitit]
    [verktyg.history :as h]))

(def default-config
  {:path-prefix "/"
   :use-fragment? true
   :dispatch-fn (fn [& args] (.log js/console "verktyg.nav" args))
   :translate-route-fn (fn [_ route] route)})

(def ^:dynamic *config*
  (atom default-config))

(def ^:dynamic *history*
  (atom nil))

(def ^:dynamic *router*
  (atom
    (reitit/router [])))

(defn set-routes!
  [routes]
  (reset! *router* (reitit/router routes)))

(defn history-push
  "Navigate to `url`, pushing a new history entry."
  [url]
  (h/history-push @*history* url))

(defn history-replace
  "Navigate to `url`, *without* pushing a new history entry (overriding the
  current one)."
  [url]
  (h/history-replace @*history* url))

(defn translate-route
  [fragments route]
  (let [f (:translate-route-fn @*config*)]
    (f fragments route)))

(defn url-to
  "Given a `route` name and the route `params` for it, returns the URL path
  for it. If no `params` are specified, the current route params are used
  instead."
  ([route]
   (url-to route {}))
  ([route params]
   {:pre [(keyword? route)
          (map? params)]
    :post [(some? %)]}
   (let [{:keys [use-fragment?]} @*config*
         token (h/current-token @*history*)
         m (reitit/match-by-path @*router* token)
         params (merge (:params m) params)
         route (translate-route (get-in m [:data :fragments]) route)
         match (reitit/match-by-name @*router* route params)
         path (:path match)]
     (if use-fragment?
       (str "#" path)
       path))))

(defn nav-handler
  "The `nav-handler` function is a callback invoked on any navigation event.
  It matches the new route against the route table, and either redirects to
  a specified `:redirect-to` target using `history-replace` or dispatches a
  `navigate` event with the route information."
  [token is-nav?]
  (let [dispatch-fn (:dispatch-fn @*config*)
        m (reitit/match-by-path @*router* token)
        path (:path m)
        data (:data m)
        params (:params m)
        route  (:name data)
        redirect-to (:redirect-to data)
        redirect-url (if (keyword? redirect-to)
                       (url-to redirect-to params)
                       redirect-to)
        fragments (conj (get data :fragments []) route)]
    (if redirect-url
      (history-replace redirect-url)
      (dispatch-fn path route fragments params))))

(defn start!
  [config routes]
  (reset! *config* (merge default-config config))
  (reset! *history* (h/make-history
                      (:use-fragment? @*config*)
                      (:path-prefix @*config*)))
  (set-routes! routes)
  (h/start-history! @*history* nav-handler))


(comment
  (def routes
    [["/" {:redirect-to "/inbox"}]
     ["/signup"
      [[""  :signup]
       ["/" :signup]
       ["/success" :signup-success]]]
     ["/self" {:fragments #{:self}}
      [[""  {:redirect-to :self-prefs}]
       ["/" {:redirect-to :self-prefs}]
       ["/preferences" :self-prefs]
       ["/general"  :self-general]
       ["/password" :self-password]]]
     ["/inbox" {:fragments #{:inbox :can-search}}
      [[""  {:name :inbox :fragments #{:tasks-index}}]
       ["/" {:name :inbox :fragments #{:tasks-index}}]
       ["/new" {:name :inbox-task-new :fragments #{:task-new :can-focus}}]
       ["/by-id/:task-id" {:fragments #{:inbox-task :can-focus}}
        [[""  {:name :inbox-task :fragments #{:task}}]
         ["/" {:name :inbox-task :fragments #{:task}}]
         ["/edit" {:name :inbox-task-edit :fragments #{:task-edit}}]]]]]
     ["/dashboards" {:fragments #{:dashboards}}
      [[""  {:redirect-to :inbox}]
       ["/" {:redirect-to :inbox}]
       ["/new" :dashboard-new]
       ["/by-id/:dashboard-id" {:fragments #{:dashboard}}
        [[""  {:redirect-to :dashboard-tasks-index}]
         ["/" {:redirect-to :dashboard-tasks-index}]
         ["/edit" :dashboard-edit]
         ["/tasks" {:fragments #{:dashboard-tasks :can-search}}
          [[""  {:name :dashboard-tasks-index :fragments #{:tasks-index}}]
           ["/" {:name :dashboard-tasks-index :fragments #{:tasks-index}}]
           ["/by-id/:task-id" {:fragments #{:dashboard-task :can-focus}}
            [[""  {:name :dashboard-task :fragments #{:task}}]
             ["/" {:name :dashboard-task :fragments #{:task}}]
             ["/edit" {:name :dashboard-task-edit :fragments #{:task-edit}}]]]]]]]]]
     ["/projects" {:fragments #{:projects}}
      [[""  :projects-index]
       ["/" :projects-index]
       ["/:ns-name/:project-name" {:fragments #{:project}}
        [[""  {:name :project-index, :redirect-to :tasks-index}]
         ["/" {:name :project-index, :redirect-to :tasks-index}]
         ["/settings" {:fragments #{:project-settings}}
          [[""  :project-settings-index]
           ["/" :project-settings-index]
           ["/team" :project-team]
           ["/admin" :project-admin]]]
         ["/notes" {:fragments #{:project-notes :can-search}}
          [[""  :notes-index]
           ["/" :notes-index]
           ["/new" {:name :note-new :fragments #{:can-focus}}]
           ["/by-id/:note-id" {:fragments #{:project-note :can-focus}}
            [[""  :note]
             ["/" :note]
             ["/edit" :note-edit]]]]]
         ["/tasks" {:fragments #{:project-tasks :can-search}}
          [[""  {:name :tasks-index :fragments #{:tasks-index}}]
           ["/" {:name :tasks-index :fragments #{:tasks-index}}]
           ["/new" {:name :project-task-new :fragments #{:task-new :can-focus}}]
           ["/by-id/:task-id" {:fragments #{:project-task :can-focus}}
            [[""  {:name :project-task :fragments #{:task}}]
             ["/" {:name :project-task :fragments #{:task}}]
             ["/edit" {:name :project-task-edit :fragments #{:task-edit}}]]]]]]]]]])

  (defn munge-route [fragments route]
    (cond
      (= route :tasks-list)
      (cond
        (contains? fragments :project)   :tasks-index
        (contains? fragments :inbox)     :inbox
        (contains? fragments :dashboard) :dashboard-tasks-index)
      (= route :task)
      (cond
        (contains? fragments :project)   :project-task
        (contains? fragments :inbox)     :inbox-task
        (contains? fragments :dashboard) :dashboard-task)
      (= route :task-edit)
      (cond
        (contains? fragments :project)   :project-task-edit
        (contains? fragments :inbox)     :inbox-task-edit
        (contains? fragments :dashboard) :dashboard-task-edit)
      (= route :task-new)
      (cond
        (contains? fragments :project) :project-task-new
        (contains? fragments :inbox)   :inbox-task-new)
      :else
      route))

  (verktyg.nav/start!
    {:translate-route-fn munge-route
     :dispatch-fn        verktyg.nav.re-frame/dispatch}
    routes))
