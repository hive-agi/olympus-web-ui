(ns olympus.graphs.primitives
  "Reusable graph visualization primitives.
   Provides node, edge, and container components for all graph types."
  (:require [reagent.core :as r]))

;; -- Configuration --

(def default-node-radius 24)
(def default-edge-stroke-width 2)

;; -- Color Helpers --

(defn status-color
  "Map status keyword to hex color."
  [status]
  (case status
    :idle "#6b7280"
    :working "#22c55e"
    :pending "#eab308"
    :running "#3b82f6"
    :completed "#22c55e"
    :approved "#22c55e"
    :rejected "#ef4444"
    :error "#ef4444"
    :spawning "#eab308"
    "#9ca3af"))

(defn type-color
  "Map type keyword to hex color."
  [type]
  (case type
    :ling "#8b5cf6"      ; purple for lings
    :entry "#3b82f6"     ; blue for KG entries
    :decision "#22c55e"  ; green for decisions
    :convention "#eab308" ; yellow for conventions
    "#6b7280"))

;; -- Node Component --

(defn node-component
  "Renders a graph node as SVG circle with label.
   Props:
   - :id - unique node identifier
   - :x, :y - center coordinates
   - :label - text to display (optional)
   - :status - status keyword for color (optional)
   - :type - type keyword for border color (optional)
   - :radius - node radius (default 24)
   - :selected? - highlight if selected
   - :on-click - click handler (receives id)"
  [{:keys [id x y label status type radius selected? on-click]
    :or {radius default-node-radius}}]
  (let [fill (status-color status)
        stroke (if type (type-color type) fill)
        hover? (r/atom false)]
    (fn [{:keys [id x y label status type radius selected? on-click]
          :or {radius default-node-radius}}]
      [:g {:class "graph-node"
           :transform (str "translate(" x "," y ")")
           :style {:cursor (when on-click "pointer")}
           :on-click (when on-click #(on-click id))
           :on-mouse-enter #(reset! hover? true)
           :on-mouse-leave #(reset! hover? false)}
       ;; Glow effect when selected or hovered
       (when (or selected? @hover?)
         [:circle {:r (+ radius 4)
                   :fill "none"
                   :stroke (status-color status)
                   :stroke-width 2
                   :opacity 0.5}])
       ;; Main circle
       [:circle {:r radius
                 :fill fill
                 :stroke stroke
                 :stroke-width (if selected? 3 2)
                 :opacity (if @hover? 1 0.9)}]
       ;; Icon/emoji in center (if short label)
       (when (and label (<= (count label) 2))
         [:text {:text-anchor "middle"
                 :dominant-baseline "central"
                 :font-size 16
                 :fill "white"
                 :style {:user-select "none"}}
          label])
       ;; Label below node (if longer)
       (when (and label (> (count label) 2))
         [:text {:y (+ radius 16)
                 :text-anchor "middle"
                 :font-size 11
                 :fill "#e5e5e5"
                 :style {:user-select "none"}}
          (if (> (count label) 15)
            (str (subs label 0 12) "...")
            label)])])))

;; -- Edge Component --

(defn edge-component
  "Renders a graph edge as SVG line or curve.
   Props:
   - :from - {:x :y} source point
   - :to - {:x :y} target point
   - :label - edge label (optional)
   - :color - stroke color (optional)
   - :width - stroke width (optional)
   - :dashed? - use dashed line
   - :arrow? - show arrowhead
   - :curved? - use bezier curve"
  [{:keys [from to label color width dashed? arrow? curved?]
    :or {color "#4b5563" width default-edge-stroke-width}}]
  (let [dx (- (:x to) (:x from))
        dy (- (:y to) (:y from))
        ;; Control point for curve (perpendicular offset)
        cx (+ (:x from) (/ dx 2) (/ dy 4))
        cy (+ (:y from) (/ dy 2) (- (/ dx 4)))
        ;; Path for curved or straight line
        path (if curved?
               (str "M " (:x from) " " (:y from)
                    " Q " cx " " cy
                    " " (:x to) " " (:y to))
               (str "M " (:x from) " " (:y from)
                    " L " (:x to) " " (:y to)))
        ;; Midpoint for label
        mid-x (if curved? cx (+ (:x from) (/ dx 2)))
        mid-y (if curved? cy (+ (:y from) (/ dy 2)))]
    [:g {:class "graph-edge"}
     ;; Edge path
     [:path {:d path
             :fill "none"
             :stroke color
             :stroke-width width
             :stroke-dasharray (when dashed? "5,5")
             :marker-end (when arrow? "url(#arrowhead)")}]
     ;; Label
     (when label
       [:text {:x mid-x
               :y (- mid-y 5)
               :text-anchor "middle"
               :font-size 10
               :fill "#9ca3af"}
        label])]))

;; -- Arrow Marker Definition --

(defn arrow-marker
  "SVG marker definition for arrowheads."
  []
  [:defs
   [:marker {:id "arrowhead"
             :markerWidth 10
             :markerHeight 7
             :refX 9
             :refY 3.5
             :orient "auto"}
    [:polygon {:points "0 0, 10 3.5, 0 7"
               :fill "#4b5563"}]]])

;; -- Graph Container --

(defn graph-container
  "SVG container with zoom/pan support.
   Props:
   - :width, :height - container dimensions
   - :children - child SVG elements
   - :on-background-click - click handler for empty space"
  [{:keys [width height on-background-click]}]
  (let [;; Local state for zoom/pan
        transform (r/atom {:scale 1 :tx 0 :ty 0})
        dragging? (r/atom false)
        drag-start (r/atom nil)

        handle-wheel (fn [e]
                       (.preventDefault e)
                       (let [delta (if (pos? (.-deltaY e)) 0.9 1.1)
                             new-scale (* (:scale @transform) delta)]
                         (when (< 0.2 new-scale 3)
                           (swap! transform assoc :scale new-scale))))

        handle-mouse-down (fn [e]
                            (when (= 0 (.-button e))  ; left button
                              (reset! dragging? true)
                              (reset! drag-start {:x (.-clientX e)
                                                  :y (.-clientY e)})))

        handle-mouse-move (fn [e]
                            (when @dragging?
                              (let [dx (- (.-clientX e) (:x @drag-start))
                                    dy (- (.-clientY e) (:y @drag-start))]
                                (swap! transform update :tx + dx)
                                (swap! transform update :ty + dy)
                                (reset! drag-start {:x (.-clientX e)
                                                    :y (.-clientY e)}))))

        handle-mouse-up (fn [_]
                          (reset! dragging? false))]

    (fn [{:keys [width height on-background-click]} & children]
      (let [{:keys [scale tx ty]} @transform]
        [:svg {:width (or width "100%")
               :height (or height "100%")
               :class "graph-container"
               :style {:background "#1a1a1a"
                       :border-radius "6px"
                       :cursor (if @dragging? "grabbing" "grab")}
               :on-wheel handle-wheel
               :on-mouse-down handle-mouse-down
               :on-mouse-move handle-mouse-move
               :on-mouse-up handle-mouse-up
               :on-mouse-leave handle-mouse-up
               :on-click (fn [e]
                           (when (and on-background-click
                                      (= (.-target e) (.-currentTarget e)))
                             (on-background-click)))}
         ;; Arrow marker definition
         [arrow-marker]
         ;; Transform group for zoom/pan
         [:g {:transform (str "translate(" tx "," ty ") scale(" scale ")")}
          ;; Render children
          (into [:<>] children)]]))))

;; -- Layout Helpers --

(defn circular-layout
  "Arrange nodes in a circle.
   Returns seq of {:id :x :y} with positions."
  [node-ids center-x center-y radius]
  (let [n (count node-ids)
        angle-step (/ (* 2 js/Math.PI) n)]
    (map-indexed
     (fn [i id]
       {:id id
        :x (+ center-x (* radius (js/Math.cos (* i angle-step))))
        :y (+ center-y (* radius (js/Math.sin (* i angle-step))))})
     node-ids)))

(defn hierarchical-layout
  "Arrange nodes in hierarchical tree layout.
   nodes: [{:id :parent-id}]
   Returns seq of {:id :x :y} with positions."
  [nodes center-x start-y level-height]
  (let [;; Group by parent
        by-parent (group-by :parent-id nodes)
        roots (get by-parent nil)

        ;; Recursive layout
        layout-level (fn layout-level [parent-ids y]
                       (let [children (mapcat #(get by-parent %) parent-ids)
                             n (count children)
                             width (* n 80)
                             start-x (- center-x (/ width 2))]
                         (when (seq children)
                           (concat
                            (map-indexed
                             (fn [i node]
                               {:id (:id node)
                                :x (+ start-x (* i 80) 40)
                                :y y})
                             children)
                            (layout-level (map :id children) (+ y level-height))))))]

    ;; Position roots at top
    (let [n (count roots)
          width (* n 100)
          start-x (- center-x (/ width 2))]
      (concat
       (map-indexed
        (fn [i node]
          {:id (:id node)
           :x (+ start-x (* i 100) 50)
           :y start-y})
        roots)
       (layout-level (map :id roots) (+ start-y level-height))))))

(defn force-directed-step
  "Single step of force-directed layout.
   nodes: [{:id :x :y}]
   edges: [{:from :to}]
   Returns updated nodes with new positions."
  [nodes edges {:keys [repulsion attraction damping]
                :or {repulsion 5000 attraction 0.05 damping 0.9}}]
  (let [node-map (into {} (map (juxt :id identity) nodes))

        ;; Calculate repulsion forces between all pairs
        repulsion-forces
        (reduce
         (fn [forces node]
           (reduce
            (fn [fs other]
              (if (= (:id node) (:id other))
                fs
                (let [dx (- (:x node) (:x other))
                      dy (- (:y node) (:y other))
                      dist (max 1 (js/Math.sqrt (+ (* dx dx) (* dy dy))))
                      force (/ repulsion (* dist dist))
                      fx (* force (/ dx dist))
                      fy (* force (/ dy dist))]
                  (-> fs
                      (update-in [(:id node) :fx] (fnil + 0) fx)
                      (update-in [(:id node) :fy] (fnil + 0) fy)))))
            forces
            nodes))
         {}
         nodes)

        ;; Calculate attraction forces along edges
        attraction-forces
        (reduce
         (fn [forces {:keys [from to]}]
           (let [n1 (get node-map from)
                 n2 (get node-map to)]
             (if (and n1 n2)
               (let [dx (- (:x n2) (:x n1))
                     dy (- (:y n2) (:y n1))
                     fx (* attraction dx)
                     fy (* attraction dy)]
                 (-> forces
                     (update-in [from :fx] (fnil + 0) fx)
                     (update-in [from :fy] (fnil + 0) fy)
                     (update-in [to :fx] (fnil - 0) fx)
                     (update-in [to :fy] (fnil - 0) fy)))
               forces)))
         repulsion-forces
         edges)]

    ;; Apply forces with damping
    (map
     (fn [node]
       (let [forces (get attraction-forces (:id node) {:fx 0 :fy 0})]
         (-> node
             (update :x + (* damping (:fx forces)))
             (update :y + (* damping (:fy forces))))))
     nodes)))
