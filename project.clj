(defproject verktyg "0.1.1-SNAPSHOT"
  :description "A utility library"
  :url "https://github.com/bwalex/verktyg"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[camel-snake-kebab "0.4.0"]
                 [com.cognitect/transit-cljs "0.8.256"]
                 [metosin/reitit-core "0.1.1"]]

  :source-paths ["src"]

  :profiles
  {:dev {:dependencies [[org.clojure/clojure "1.9.0"]
                        [org.clojure/clojurescript "1.10.238"]
                        [binaryage/devtools "0.9.9"]
                        [cider/piggieback "0.3.1"]
                        [reagent "0.8.0-alpha2"]
                        [re-frame "0.10.5"]]

         :cljsbuild {:builds
                     [{:id "dev"
                       :source-paths ["src", "dev"]
                       :compiler {:main verktyg.dev
                                  :npm-deps {:create-emotion "9.2.6"
                                             :create-emotion-server "9.2.6"}
                                  :install-deps false
                                  :output-to "dev.js"
                                  :output-dir "target/js/dev"
                                  :target :nodejs
                                  :optimizations :none
                                  :closure-defines {process.env/NODE_ENV "development"}
                                  :source-map-timestamp true}}]}

         :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
         :plugins [[lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]}})
