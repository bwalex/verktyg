(ns verktyg.styled
  (:require
    [clojure.string]))

;; from roman01la/cljss
(defmacro var->cmp-name [sym]
    `(-> ~'&env :ns :name (clojure.core/str "." ~sym)))

(defmacro defstyled [var tag & style-parts]
  (let [cmp-name# (var->cmp-name var)
        parts# (vec style-parts)]
    `(def ~var
       (reagent.core/create-class
         {:display-name ~cmp-name#
          :reagent-render
          (fn [props# & children#]
            (let [css-fn# (apply verktyg.styled/css ~parts#)
                  cls-names# (verktyg.styled/cx-merge (str (css-fn# props#) " " (get props# :class "")))
                  new-props# (merge props# {:class cls-names#})
                  children# (if (empty? children#)
                              (get props# :children [])
                              children#)
                  filt-props# (if (keyword? ~tag)
                                (verktyg.styled/filter-props new-props#)
                                new-props#)]
              (into [~tag filt-props#] children#)))}))))
