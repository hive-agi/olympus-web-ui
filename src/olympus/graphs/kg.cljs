(ns olympus.graphs.kg
  "Knowledge Graph visualization.
   Shows memory entries as nodes with relationship edges.
   Staleness affects node opacity."
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [olympus.graphs.primitives :as p]
            [olympus.subs :as subs]))

;; -- Entry Type Styling --

(defn entry-type-color
  "Color for memory entry type."
  [type]
  (case type
    :note "#3b82f6"       ; blue
    :snippet "#10b981"    ; emerald
    :convention "#eab308" ; yellow
    :decision "#8b5cf6"   ; purple
    :axiom "#ef4444"      ; red (high importance)
    "#6b7280"))

(defn entry-type-icon
  "Icon for memory entry type."
  [type]
  (case type
    :note "N"
    :snippet "S"
    :convention "C"
    :decision "D"
    :axiom "!"
    "?"))

;; -- Relation Styling --

(defn relation-color
  "Color for KG edge relation type."
  [relation]
  (case relation
    :implements "#22c55e"   ; green
    :supersedes "#ef4444"   ; red
    :refines "#3b82f6"      ; blue
    :contradicts "#f97316"  ; orange
    :depends-on "#8b5cf6"   ; purple
    :derived-from "#eab308" ; yellow
    :applies-to "#6b7280"   ; gray
    "#4b5563"))

(defn relation-label
  "Human-readable label for relation."
  [relation]
  (case relation
    :implements "implements"
    :supersedes "supersedes"
    :refines "refines"
    :contradicts "contradicts!"
    :depends-on "depends on"
    :derived-from "derived from"
    :applies-to "applies to"
    (name relation)))

;; -- Entry Node --

(defn entry-node
  "Renders a KG entry as a node.
   Staleness affects opacity.
   Props:
   - entry: {:id :type :tags :staleness-depth :content}
   - x, y: position
   - selected?: highlight state
   - on-click: handler"
  [{:keys [entry x y selected? on-click]}]
  (let [{:keys [id type staleness-depth]} entry
        ;; Calculate opacity based on staleness (0 = fresh, higher = stale)
        opacity (max 0.3 (- 1 (* (or staleness-depth 0) 0.15)))]
    [:g {:opacity opacity}
     [p/node-component
      {:id id
       :x x
       :y y
       :label (entry-type-icon type)
       :status (if (< (or staleness-depth 0) 2) :idle :pending)
       :type (keyword type)
       :radius 20
       :selected? selected?
       :on-click on-click}]]))

;; -- KG Edge --

(defn kg-edge
  "Renders a KG relationship edge.
   Props:
   - edge: {:from :to :relation :confidence}
   - from-pos, to-pos: {:x :y} positions"
  [{:keys [edge from-pos to-pos]}]
  (let [{:keys [relation confidence]} edge]
    [p/edge-component
     {:from from-pos
      :to to-pos
      :label (relation-label relation)
      :color (relation-color relation)
      :width (+ 1 (* (or confidence 0.5) 2))
      :arrow? true
      :curved? true}]))

;; -- Force-Directed Layout State --

(defn initial-positions
  "Generate initial random positions for entries."
  [entry-ids center-x center-y spread]
  (map
   (fn [id]
     {:id id
      :x (+ center-x (* (- (rand) 0.5) spread))
      :y (+ center-y (* (- (rand) 0.5) spread))})
   entry-ids))

;; -- Knowledge Graph Visualization --

(defn kg-graph
  "Interactive knowledge graph visualization.
   Uses force-directed layout for positioning.
   Props:
   - entries: map of entry-id -> entry data
   - edges: seq of {:from :to :relation :confidence}
   - width, height: dimensions
   - on-entry-click: handler"
  [{:keys [entries edges width height on-entry-click]}]
  (let [;; State
        selected (r/atom nil)
        positions (r/atom nil)
        animating? (r/atom false)

        ;; Initialize positions on first render
        init-positions! (fn []
                          (let [entry-ids (keys entries)
                                center-x (/ (or width 800) 2)
                                center-y (/ (or height 500) 2)]
                            (reset! positions
                                    (into {}
                                          (map (juxt :id identity)
                                               (p/circular-layout entry-ids center-x center-y 150))))))

        ;; Run force simulation step
        simulate-step! (fn []
                         (when @positions
                           (let [nodes (vals @positions)
                                 edge-list (map (fn [e] {:from (:from e) :to (:to e)}) edges)
                                 new-nodes (p/force-directed-step nodes edge-list
                                                                  {:repulsion 3000
                                                                   :attraction 0.03
                                                                   :damping 0.85})]
                             (reset! positions (into {} (map (juxt :id identity) new-nodes))))))]

    ;; Initialize on mount
    (r/create-class
     {:component-did-mount
      (fn [_]
        (init-positions!)
        ;; Run a few simulation steps to stabilize
        (reset! animating? true)
        (dotimes [_ 50]
          (simulate-step!))
        (reset! animating? false))

      :reagent-render
      (fn [{:keys [entries edges width height on-entry-click]}]
        (let [pos-map @positions]
          [p/graph-container
           {:width width
            :height height
            :on-background-click #(reset! selected nil)}

           ;; Type legend
           [:g {:transform "translate(20, 20)"}
            [:text {:y 0 :font-size 12 :fill "#9ca3af"} "Entry Types:"]
            (for [[i [type label]] (map-indexed vector
                                                [[:note "Note"]
                                                 [:snippet "Snippet"]
                                                 [:convention "Convention"]
                                                 [:decision "Decision"]
                                                 [:axiom "Axiom"]])]
              ^{:key type}
              [:g {:transform (str "translate(0, " (+ 20 (* i 18)) ")")}
               [:circle {:cx 10 :r 6 :fill (entry-type-color type)}]
               [:text {:x 22 :y 4 :font-size 10 :fill "#e5e5e5"} label]])]

           ;; Relation legend
           [:g {:transform (str "translate(" (- (or width 800) 140) ", 20)")}
            [:text {:y 0 :font-size 12 :fill "#9ca3af"} "Relations:"]
            (for [[i [rel label]] (map-indexed vector
                                               [[:implements "implements"]
                                                [:supersedes "supersedes"]
                                                [:refines "refines"]
                                                [:depends-on "depends on"]])]
              ^{:key rel}
              [:g {:transform (str "translate(0, " (+ 20 (* i 16)) ")")}
               [:line {:x1 0 :y1 0 :x2 20 :y2 0
                       :stroke (relation-color rel)
                       :stroke-width 2}]
               [:text {:x 25 :y 4 :font-size 9 :fill "#9ca3af"} label]])]

           ;; Draw edges first (behind nodes)
           (when pos-map
             (for [edge edges
                   :let [from-pos (get pos-map (:from edge))
                         to-pos (get pos-map (:to edge))]
                   :when (and from-pos to-pos)]
               ^{:key (str (:from edge) "-" (:to edge))}
               [kg-edge {:edge edge
                         :from-pos from-pos
                         :to-pos to-pos}]))

           ;; Draw entry nodes
           (when pos-map
             (for [[entry-id entry] entries
                   :let [pos (get pos-map entry-id)]
                   :when pos]
               ^{:key entry-id}
               [entry-node
                {:entry (assoc entry :id entry-id)
                 :x (:x pos)
                 :y (:y pos)
                 :selected? (= entry-id @selected)
                 :on-click (fn [id]
                             (reset! selected id)
                             (when on-entry-click
                               (on-entry-click id)))}]))

           ;; Selected entry details
           (when (and @selected (get entries @selected))
             (let [entry (get entries @selected)
                   panel-x 20
                   panel-y (- (or height 500) 180)]
               [:g {:class "entry-details"}
                ;; Background
                [:rect {:x panel-x :y panel-y
                        :width 350 :height 160
                        :rx 6
                        :fill "#252525"
                        :stroke "#333"
                        :stroke-width 1}]
                ;; Title
                [:text {:x (+ panel-x 10) :y (+ panel-y 25)
                        :font-size 13
                        :font-weight "bold"
                        :fill (entry-type-color (:type entry))}
                 (str (entry-type-icon (:type entry)) " "
                      (name (or (:type entry) :unknown)))]
                ;; ID
                [:text {:x (+ panel-x 10) :y (+ panel-y 45)
                        :font-size 10
                        :fill "#6b7280"}
                 (str "ID: " @selected)]
                ;; Tags
                [:text {:x (+ panel-x 10) :y (+ panel-y 65)
                        :font-size 10
                        :fill "#9ca3af"}
                 (str "Tags: " (clojure.string/join ", " (take 5 (or (:tags entry) []))))]
                ;; Staleness
                [:text {:x (+ panel-x 10) :y (+ panel-y 85)
                        :font-size 10
                        :fill (if (< (or (:staleness-depth entry) 0) 2)
                                "#22c55e" "#eab308")}
                 (str "Staleness: " (or (:staleness-depth entry) 0))]
                ;; Content preview
                [:foreignObject {:x (+ panel-x 10) :y (+ panel-y 95)
                                 :width 330 :height 55}
                 [:div {:style {:font-size "10px"
                                :color "#a3a3a3"
                                :overflow "hidden"
                                :text-overflow "ellipsis"
                                :max-height "55px"}}
                  (let [content (str (:content entry))]
                    (if (> (count content) 200)
                      (str (subs content 0 197) "...")
                      content))]]]))]))})))

;; -- KG Stats --

(defn kg-stats
  "Summary statistics for knowledge graph."
  []
  (let [entries @(rf/subscribe [::subs/kg-entries])
        edges @(rf/subscribe [::subs/kg-edges])
        entry-list (vals entries)
        by-type (group-by :type entry-list)
        fresh (count (filter #(< (or (:staleness-depth %) 0) 2) entry-list))
        stale (- (count entry-list) fresh)]
    [:div {:class "kg-stats"}
     [:div {:class "stat"}
      [:span {:class "stat-value"} (count entries)]
      [:span {:class "stat-label"} "Entries"]]
     [:div {:class "stat"}
      [:span {:class "stat-value"} (count edges)]
      [:span {:class "stat-label"} "Edges"]]
     [:div {:class "stat"}
      [:span {:class "stat-value" :style {:color "#22c55e"}} fresh]
      [:span {:class "stat-label"} "Fresh"]]
     [:div {:class "stat"}
      [:span {:class "stat-value" :style {:color "#eab308"}} stale]
      [:span {:class "stat-label"} "Stale"]]
     [:div {:class "stat"}
      [:span {:class "stat-value" :style {:color "#8b5cf6"}} (count (get by-type :decision))]
      [:span {:class "stat-label"} "Decisions"]]]))

;; -- Filter Controls --

(defn kg-filters
  "Filter controls for KG visualization."
  []
  (let [filters (r/atom {:types #{:note :snippet :convention :decision :axiom}
                         :min-confidence 0
                         :show-stale? true})]
    (fn []
      [:div {:class "kg-filters"}
       [:span {:class "filter-label"} "Filter by type:"]
       (for [type [:note :snippet :convention :decision :axiom]]
         ^{:key type}
         [:label {:class "filter-checkbox"}
          [:input {:type "checkbox"
                   :checked (contains? (:types @filters) type)
                   :on-change #(swap! filters update :types
                                      (if (contains? (:types @filters) type)
                                        disj conj)
                                      type)}]
          [:span {:style {:color (entry-type-color type)}}
           (name type)]])])))

;; -- Integrated KG Panel --

(defn kg-panel
  "Complete knowledge graph visualization panel."
  []
  (let [entries @(rf/subscribe [::subs/kg-entries])
        edges @(rf/subscribe [::subs/kg-edges])]
    [:div {:class "panel kg-panel"}
     [:h2 "Knowledge Graph"]
     [kg-stats]
     [:div {:class "kg-graph-container"
            :style {:height "500px"}}
      (if (empty? entries)
        [:div {:class "empty-state"}
         [:span {:style {:font-size "2rem"}} "K"]
         [:p "No knowledge entries"]
         [:p {:class "hint"} "Memories will appear here as nodes"]]
        [kg-graph
         {:entries entries
          :edges edges
          :width "100%"
          :height 500
          :on-entry-click #(js/console.log "Entry clicked:" %)}])]]))
