(ns verktyg.jdbc
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str])
  (:import
    org.postgresql.util.PGobject
    java.sql.Array
    clojure.lang.IPersistentMap
    clojure.lang.IPersistentVector
    [java.sql
     BatchUpdateException
     PreparedStatement]))

(extend-protocol jdbc/IResultSetReadColumn
  Array
  (result-set-read-column [v _ _] (vec (.getArray v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value))))

(defn to-pg-json [value]
  (doto (PGobject.)
        (.setType "jsonb")
        (.setValue (generate-string value))))

(extend-type clojure.lang.IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long idx]
    (let [conn      (.getConnection stmt)
          meta      (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_) (str/join (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt idx (to-pg-json v))))))


(extend-protocol jdbc/ISQLParameter
  clojure.lang.IPersistentMap
  (set-parameter [^IPersistentMap v ^PreparedStatement s ^long i]
    (jdbc/set-parameter (to-pg-json v) s i))
  clojure.lang.Seqable
  (set-parameter [seqable ^PreparedStatement s ^long i]
    (jdbc/set-parameter (vec (seq seqable)) s i)))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))
