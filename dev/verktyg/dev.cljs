(ns verktyg.dev)

(require 'cljs.repl)
(require 'cljs.repl.node)
(cljs.repl/repl (cljs.repl.node/repl-env))

(require 'verktyg.styled :reload)
((verktyg.styled/css {:background-color "red"} (fn [props] {:color "blue"}) {:font-size "9em"}))
verktyg.styled/*emotion*

;; cljs.user=> (require 'verktyg.styled)
;; nil
;;
;; cljs.user=> ((verktyg.styled/css {:background-color "red"} (fn [props] {:color "blue"}) {:font-size "9em"}))
;; "css-z1gi0e"
;;
;; cljs.user=> verktyg.styled/*emotion*
;; #js {:flush #object[flush], :hydrate #object[hydrate], :cx #object[cx], :merge #object[merge], :getRegisteredStyles #object[getRegisteredStyles], :injectGlobal #object[injectGlobal], :keyframes #object[keyframes], :css #object[css], :sheet #object[StyleSheet [object Object]], :caches #js {:registered #js {:css-z1gi0e "background-color:red;color:blue;font-size:9em;"}, :inserted #js {:z1gi0e ".css-z1gi0e{background-color:red;color:blue;font-size:9em;}"}, :nonce nil, :key "css"}}
;;
;; cljs.user=> (require 'verktyg.styled.ssr)
;; nil
;;
;; cljs.user=> (verktyg.styled.ssr/html->styles-string "<a class=\"css-z1gi0e\">Hello</a>")
;; "<style data-emotion-css=\"z1gi0e\">.css-z1gi0e{background-color:red;color:blue;font-size:9em;}</style><a class=\"css-z1gi0e\">Hello</a>"
