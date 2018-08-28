(ns verktyg.styled
  (:require
    [clojure.string]
    [camel-snake-kebab.core :refer [->camelCaseString]]
    [camel-snake-kebab.extras :refer [transform-keys]]
    [create-emotion :as createEmotion])
  (:require-macros verktyg.styled))

(defonce ^:dynamic
  *emotion*
  (createEmotion #js {}))

;; from filter-react-dom-props
(def dom-props
  [:abbr
   :accept
   :accept-charset
   :access-key
   :action
   :allow-full-screen
   :allow-transparency
   :alt
   :async
   :auto-complete
   :auto-focus
   :auto-play
   :cell-padding
   :cell-spacing
   :challenge
   :charset
   :checked
   :cite
   :class
   :class-name
   :cols
   :col-span
   :command
   :content
   :content-editable
   :context-menu
   :controls
   :coords
   :cross-origin
   :data
   :data-key
   :date-time
   :default
   :defer
   :dir
   :disabled
   :download
   :draggable
   :dropzone
   :enc-type
   :for
   :form
   :form-action
   :form-enc-type
   :form-method
   :form-no-validate
   :form-target
   :frame-border
   :headers
   :height
   :hidden
   :high
   :href
   :href-lang
   :html-for
   :http-equiv
   :icon
   :id
   :input-mode
   :is-map
   :item-id
   :item-prop
   :item-ref
   :item-scope
   :item-type
   :key
   :kind
   :label
   :lang
   :list
   :loop
   :manifest
   :max
   :max-length
   :media
   :media-group
   :method
   :min
   :min-length
   :multiple
   :muted
   :name
   :no-validate
   :open
   :optimum
   :pattern
   :ping
   :placeholder
   :poster
   :preload
   :radio-group
   :read-only
   :ref
   :rel
   :required
   :role
   :rows
   :row-span
   :sandbox
   :scope
   :scoped
   :scrolling
   :seamless
   :selected
   :shape
   :size
   :sizes
   :sortable
   :span
   :spell-check
   :src
   :src-doc
   :src-set
   :start
   :step
   :style
   :tab-index
   :target
   :title
   :translate
   :type
   :type-must-match
   :use-map
   :value
   :width
   :wmode
   :wrap
   :on-copy
   :on-cut
   :on-paste
   :on-load
   :on-error
   :on-wheel
   :on-scroll
   :on-composition-end
   :on-composition-start
   :on-composition-update
   :on-key-down
   :on-key-press
   :on-key-up
   :on-focus
   :on-blur
   :on-change
   :on-input
   :on-submit
   :on-click
   :on-context-menu
   :on-double-click
   :on-drag
   :on-drag-end
   :on-drag-enter
   :on-drag-exit
   :on-drag-leave
   :on-drag-over
   :on-drag-start
   :on-drop
   :on-mouse-down
   :on-mouse-enter
   :on-mouse-leave
   :on-mouse-move
   :on-mouse-out
   :on-mouse-over
   :on-mouse-up
   :on-select
   :on-touch-cancel
   :on-touch-end
   :on-touch-move
   :on-touch-start
   :on-animation-start
   :on-animation-end
   :on-animation-iteration
   :on-transition-end])

(defn filter-props [props]
  (select-keys props dom-props))

(defn clj->emotion-js
  [call-args part]
  (when-let [x (if (fn? part)
                 (apply part call-args)
                 part)]
    (if (string? x)
      x
      (transform-keys
        (fn [s]
          (if (keyword? s)
            (->camelCaseString s)
            s))
        x))))

(defn css-str [call-args part]
  {:pre [(or (string? part)
             (ifn? part))]}
  (if (ifn? part)
    (apply part call-args)
    part))

(defn make-emotion-fn
  [css-fn parts]
  {:pre [(coll? parts)
         (fn? css-fn)]}
  (fn [& args]
    (let [obj-syntax? (not (string? (first parts)))
          style
          (if obj-syntax?
            (->> parts
                 (reduce
                   (fn [a v]
                     (if v
                       (conj a (clj->emotion-js args v))
                       a))
                   [])
                 clj->js)
            (reduce #(str %1 (css-str args %2)) "" parts))]
      (css-fn style))))

(defn css [& parts]
  (make-emotion-fn (.-css *emotion*) parts))

(defn cx-merge [class-name]
  {:pre [(string? class-name)]}
  ((.-merge *emotion*) class-name))

(defn inject-global [& parts]
  (make-emotion-fn (.-injectGlobal *emotion*) parts))

(defn styled [c & style-parts]
  (let [css-fn (apply css style-parts)]
    (fn [props & children]
      (let [class-name (cx-merge (str (css-fn props) " " (get props :class "")))
            new-props  (assoc props :class class-name)
            filt-props (if (keyword? c)
                         (filter-props new-props)
                         new-props)]
        (into [c filt-props] children)))))
