(ns verktyg.validation
  (:require
    [clojure.string :as str]
    #?(:clj [verktyg.core :refer [when-require]])))

(defn ^:private -run-validator
  [validator value]
  {:pre [(and (coll? validator)
              (pos? (count validator)))]}
  (let [val-fn (first validator)
        args   (rest validator)]
    (cond
      (= value ::missing) [nil (when (= val-fn ::required) "is required")]
      (nil? value)        [nil (when (= val-fn ::required) "must not be nil")]
      (keyword? val-fn)   [value nil]
      :else               (apply val-fn value args))))

(defn ^:private -run-validators
  [validators value]
  {:pre [(coll? validators)]}
  (reduce (fn [[v _] validator]
            (let [[value err-str] (-run-validator validator v)]
              (if err-str
                (reduced [v err-str])
                [value nil])))
    [value nil]
    validators))

(defn ^:private -field-err-str
  [k err]
  (when err (str "" k " " err)))

(defn validate-kv
  [value fields]
  (reduce (fn [[res errs] [k vs]]
            (let [c?      (contains? value k)
                  [v err] (-run-validators vs (get value k ::missing))
                  err-str (-field-err-str k err)
                  errs    (if err (assoc errs k err-str) errs)]
              [(if c? (assoc res k v) res)
               errs]))
          [{} {}]
          fields))

(defn validate-kv-or-throw
  [value fields ex]
  (let [[v err-map] (validate-kv value fields)]
    (when (seq err-map)
      (throw ex))
    v))

(defn condense-errors
  [err-coll]
  (cond
    (map? err-coll)
    (str/join ", "
              (->> err-coll
                   vals
                   (map condense-errors)
                   (remove nil?)))
    (coll? err-coll)
    (str/join ", "
              (->> err-coll
                   (map condense-errors)
                   (remove nil?)))
    :else
    err-coll))

(defn validate
  [value fields]
  (let [[v err-map] (validate-kv value fields)]
    [v
     (reduce
       (fn [errs [_ v]]
         (if v
           (conj errs (condense-errors v))
           errs))
       []
       err-map)]))

(defn validate-or-throw
  [value fields ex-fn]
  (let [[v errs] (validate value fields)]
    (when (seq errs)
      (throw (ex-fn errs)))
    v))

(defn each
  [value & {:keys [validators msg]}]
  (let [[v err] (reduce
                  (fn [[a _] v]
                    (let [[value err-str] (-run-validators validators v)]
                      (if err-str
                        (reduced [a err-str])
                        [(conj a value) nil])))
                  [[] nil]
                  value)]
    [v (when err (or msg (str "each element " err)))]))

(defn each-list
  "Like `each`, but keeps errors as an error vector instead of flattening into
  a single string. The error vector is `nil` if no error occurs, otherwise it
  has as many elements as `value`, each element either `nil` if the given
  element did not have an error or an error element (map, vector or string,
  depending on the validators used)."
  [value & {:keys [validators]}]
  (let [[v errs] (reduce
                   (fn [[a errs] v]
                     (let [[value err-str] (-run-validators validators v)]
                       [(conj a value)
                        (conj errs err-str)]))
                   [[] []]
                   value)]
    [v (when (seq errs) errs)]))

(defn nested
  [value & {:keys [fields msg]}]
  (let [[v errs] (validate value fields)]
    [v
     (when (seq errs) (or msg (str/join ", " errs)))]))

(defn nested-map
  "Like `nested`, but keeps errors as an error map instead of flattening into
  a single string. The error map is `nil` if no error occurs, otherwise it
  contains only those keys that had an error. The values are error elements
  (map, vector or string, depending on the validators used)."
  [value & {:keys [fields]}]
  (let [[v errm] (validate-kv value fields)]
    [v
     (when (seq errm) errm)]))

(defn one-of
  [value & validators]
  (let [errs
        (reduce
          (fn [errs validator]
            (let [[value err] (-run-validators validator value)]
              (if (nil? err)
                (reduced [])
                (conj errs err))))
          []
          validators)]
    [value
     (when (seq errs)
       (str "does not match any validator: "
            (str/join ", " errs)))]))

(defn string
  [value & {:keys [min max msg]
            :or {min 0 max 64
                 msg #(str "must be a string between " %1 " and " %2 " characters")}}]
  (let [err-str (if (fn? msg) (msg min max) msg)]
    (if (and (string? value)
             (>= (count value) min)
             (<= (count value) max))
      [value nil]
      [nil err-str])))

(defn re
  [value & {:keys [expr msg]
            :or {msg #(str "must match the regular expression " %)}}]
  (let [err-str (if (fn? msg) (msg expr) msg)]
    (if (and (string? value)
             (re-matches expr value))
      [value nil]
      [nil err-str])))

(defn coll
  [value & {:keys [min max msg]
            :or {min 0 max 128
                 msg #(str "must be a collection with " %1 " to " %2 " elements")}}]
  (let [err-str (if (fn? msg) (msg min max) msg)]
    (if (and (coll? value)
             (>= (count value) min)
             (<= (count value) max))
      [value nil]
      [[] err-str])))

(defn with
  [value & {:keys [pred msg]
            :or {msg "fails predicate"}}]
  {:pre [(ifn? pred)]}
  (if (pred value)
    [value nil]
    [nil msg]))

(defn try-catch
  [value & {:keys [f msg]}]
  {:pre [(fn? f)]}
  (try
    [(f value) nil]
    #?(:clj  (catch Exception e
               [nil (or msg (.getMessage e))])
       :cljs (catch :default e
               [nil (or msg (.-message e))]))))

(defn to-set
  [value & _]
  [(set value) nil])

(defn integer
  [value & {:keys [min max msg]
            :or {min #?(:clj  Integer/MIN_VALUE
                        :cljs (.-MIN_SAFE_INTEGER js/Number))
                 max #?(:clj  Integer/MAX_VALUE
                        :cljs (.-MAX_SAFE_INTEGER js/Number))
                 msg #(str "must be an integer between " %1 " and " %2)}}]
  (let [err-str (if (fn? msg) (msg min max) msg)]
    (if (and (integer? value)
             (>= value min)
             (<= value max))
      [value nil]
      [nil err-str])))

(defn number
  [value & {:keys [min max msg]
            :or {min #?(:clj  Double/NEGATIVE_INFINITY
                        :cljs (.-NEGATIVE_INFINITY js/Number))
                 max #?(:clj  Double/POSITIVE_INFINITY
                        :cljs (.-POSITIVE_INFINITY js/Number))
                 msg #(str "must be a number between " %1 " and " %2)}}]
  (let [err-str (if (fn? msg) (msg min max) msg)]
    (if (and (number? value)
             (>= value min)
             (<= value max))
      [value nil]
      [nil err-str])))

(defn bool
  [value & {:keys [coerce]
            :or {coerce false}}]
  (let [err-str "must be a boolean"
        ok (or (boolean? value) coerce)]
    [(boolean value) (when-not ok err-str)]))

(defn kw
  [value & {:keys [coerce]
            :or {coerce false}}]
  (let [err-str "must be a keyword"
        ok (or (keyword? value) coerce)]
    (cond
      (keyword? value)
      [value nil]
      coerce
      (try
        [(keyword value) nil]
        #?(:clj  (catch ClassCastException e
                   [value err-str])
           :cljs (catch :default e
                   [value err-str])))
      :else
      [value err-str])))

(defn exactly=
  [value v]
  (let [err-str (str "must be exactly " v)]
    [value (when (not= value v) err-str)]))

(defn email
  [value]
  (if (re-matches #".+\@.+\..+" value)
    [value nil]
    [nil "must be a valid email address"]))

(defn pass
  [value & _]
  [value nil])

#?(:clj
   (when-require 'clj-uuid
     (defn uuid
       [value & {:keys [coerce]
                 :or {coerce false}}]
       (let [err-str (str "must be a UUID")
             ok (if coerce
                  (clj-uuid/uuidable? value)
                  (clj-uuid/uuid? value))]
         (if ok
           [(clj-uuid/as-uuid value) nil]
           [nil err-str])))))

#?(:clj
   (when-require 'clj-time.format
     (defn datetime
       [value & {:keys [formatter] :or {formatter nil}}]
       (try
         [(if formatter (clj-time.format/parse formatter value) (clj-time.format/parse value)) nil]
         (catch Exception e
           [nil "Must be a valid date/time"]))))
   :cljs
   (defn datetime
     [value & _]
     (let [d (.parse js/Date value)]
       (if-not (js/isNaN d)
         [d nil]
         [nil "Must be a valid date/time"]))))

#?(:clj
   (when-require 'cheshire.core
     (defn json
       [value & {:keys [keywordize]
                 :or {keywordize true}}]
       (try
         [(cheshire.core/parse-string value keywordize) nil]
         (catch Exception e
           [nil "Must be valid json"]))))
   :cljs
   (defn json
     [value & {:keys [keywordize]
               :or {keywordize true}}]
     (try
       [(js->clj (.parse js/JSON value) :keywordize-keys true) nil]
       (catch :default e
         [nil "must be valid json"]))))

(def required [::required])
