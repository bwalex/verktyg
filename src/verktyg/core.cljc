(ns verktyg.core
  (:require
    [clojure.string :as str]
    #?(:cljs [cognitect.transit])))

#?(:clj
   (defn require-resolve
     "Requires and resolves the given namespace-qualified symbol."
     [sym]
     (require (symbol (namespace sym)))
     (resolve sym)))

#?(:clj
   (defmacro if-require
     ([n then     ] `(if-require ~n ~then nil))
     ([n then else]
      "Evaluate `body` if the namespace `n` can be required and resolved."
      (try
        (require (eval n))
        (catch Throwable e
          nil))
      (if (find-ns (eval n))
        then
        else))))

#?(:clj
   (defmacro when-require
     "Evaluate `body` if the namespace `n` can be required and resolved."
     [n & body] `(if-require ~n (do ~@body))))

;; From: ptaoussanis/encore / com.taoensso/encore
(defmacro if-let*
  "Like `core/if-let` but can bind multiple values for `then` iff all tests
  are truthy, supports internal unconditional `:let`s."
  ([bindings then     ] `(if-let* ~bindings ~then nil))
  ([bindings then else]
   (let [s (seq bindings)]
     (if s ; (if-let [] true false) => true
       (let [[b1 b2 & bnext] s]
         (if (= b1 :let)
           `(let      ~b2  (if-let* ~(vec bnext) ~then ~else))
           `(let [b2# ~b2]
              (if b2#
                (let [~b1 b2#]
                  (if-let* ~(vec bnext) ~then ~else))
                ~else))))
       then))))

(defmacro when-let*
  "Like `core/when-let` but can bind multiple values for `body` iff all tests
  are truthy, supports internal unconditional `:let`s."
  ;; Now a feature subset of all-case `when`
  [bindings & body] `(if-let* ~bindings (do ~@body)))


;; -- Fuzzy substring search --------------------------------------------------

(defn fuzzy-substr [fields filter-str coll]
  (if (empty? filter-str)
    coll
    (let [filt (str/lower-case filter-str)]
      (filter
        (fn [el]
          (some (fn [f]
                  (let [v (str/lower-case (get el f ""))]
                    (str/includes? v filt)))
                fields))
        coll))))


;; -- Simple JS interop helpers -----------------------------------------------

#?(:cljs
   (defn fetch [url opts]
     (let [opts (clj->js opts)]
       (.fetch js/window url opts))))

#?(:cljs
   (do
     (defn ->json [v]
       (.stringify js/JSON (clj->js v)))

     (defn json->clj [v]
       (when v
         (js->clj (.parse js/JSON v)
                  :keywordize-keys true)))

     (defn ->transit [v]
       (let [writer (cognitect.transit/writer :json)]
         (cognitect.transit/write writer v)))

     (defn transit->clj [v]
       (let [reader (cognitect.transit/reader :json)]
         (cognitect.transit/read reader v)))))

#?(:cljs
   (do
     (defn set-local-store [ls-key data]
       (.setItem js/localStorage
                 (name ls-key)
                 (->transit data)))

     (defn get-local-store [ls-key]
       (transit->clj (.getItem js/localStorage (name ls-key))))))
