(ns verktyg.styled.ssr
  (:require
    [verktyg.styled :refer [*emotion*]]
    [create-emotion-server :as createEmotionServer]))

(defn html->styles-string
  [html]
  (let [es (createEmotionServer *emotion*)
        f  (.-renderStylesToString es)]
    (f html)))

(defn html->styles-node-stream
  [html]
  (let [es (createEmotionServer *emotion*)
        f  (.-renderStylesToNodeStream es)]
    (f html)))
