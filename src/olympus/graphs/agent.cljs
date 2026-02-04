(ns olympus.graphs.agent
  "Agent topology visualization.
   Shows ling/drone hierarchy with status and coordination edges."
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [olympus.graphs.primitives :as p]
            [olympus.subs :as subs]))

;; -- Agent Type Styling --

(defn agent-icon
  "Icon/emoji for agent type."
  [type]
  (case type
    :ling "L"      ; Ling (coordinator)
    :drone "D"     ; Drone (worker)
    :coordinator "C"  ; Hivemind coordinator
    "?"))

(defn agent-type-color
  "Border color for agent type."
  [type]
  (case type
    :ling "#8b5cf6"       ; purple
    :drone "#f97316"      ; orange
    :coordinator "#ec4899" ; pink
    "#6b7280"))

;; -- Agent Node --

(defn agent-node
  "Renders an agent as a graph node.
   Props:
   - agent: {:id :type :status :task :parent-id}
   - x, y: position
   - selected?: highlight state
   - on-click: handler"
  [{:keys [agent x y selected? on-click]}]
  (let [{:keys [id type status task]} agent]
    [p/node-component
     {:id id
      :x x
      :y y
      :label (agent-icon type)
      :status status
      :type type
      :radius (if (= type :coordinator) 32 24)
      :selected? selected?
      :on-click on-click}]))

;; -- Parent-Child Edge --

(defn hierarchy-edge
  "Edge from parent to child agent."
  [{:keys [parent child]}]
  [p/edge-component
   {:from {:x (:x parent) :y (+ (:y parent) 24)}
    :to {:x (:x child) :y (- (:y child) 24)}
    :color (agent-type-color (:type parent))
    :arrow? true
    :curved? false}])

;; -- Coordination Edge --

(defn coordination-edge
  "Dashed edge showing coordination/communication."
  [{:keys [from to]}]
  [p/edge-component
   {:from {:x (:x from) :y (:y from)}
    :to {:x (:x to) :y (:y to)}
    :color "#4b5563"
    :dashed? true
    :arrow? false}])

;; -- Agent Graph Layout --

(defn layout-agents
  "Arrange agents in hierarchical layout.
   Returns agents with :x :y positions added."
  [agents center-x center-y]
  (let [;; Separate by type
        coordinator (first (filter #(= :coordinator (:type %)) agents))
        lings (filter #(= :ling (:type %)) agents)
        drones (filter #(= :drone (:type %)) agents)

        ;; Group drones by parent
        drones-by-parent (group-by :parent-id drones)

        ;; Layout coordinator at top
        coord-y (- center-y 120)

        ;; Layout lings in a row below coordinator
        n-lings (count lings)
        ling-spacing 120
        ling-start-x (- center-x (/ (* (dec n-lings) ling-spacing) 2))
        ling-y center-y

        positioned-lings
        (map-indexed
         (fn [i ling]
           (assoc ling
                  :x (+ ling-start-x (* i ling-spacing))
                  :y ling-y))
         lings)

        ;; Layout drones below their parent lings
        drone-y (+ center-y 100)
        positioned-drones
        (mapcat
         (fn [ling]
           (let [children (get drones-by-parent (:id ling))
                 n (count children)
                 spacing 50
                 start-x (- (:x ling) (/ (* (dec n) spacing) 2))]
             (map-indexed
              (fn [i drone]
                (assoc drone
                       :x (+ start-x (* i spacing))
                       :y drone-y))
              children)))
         positioned-lings)]

    ;; Combine all positioned agents
    (concat
     (when coordinator
       [(assoc coordinator :x center-x :y coord-y)])
     positioned-lings
     positioned-drones)))

;; -- Agent Topology Graph --

(defn agent-topology-graph
  "Renders the full agent hierarchy graph.
   Props:
   - agents: map of agent-id -> agent data
   - width, height: dimensions
   - on-agent-click: handler"
  [{:keys [agents width height on-agent-click]}]
  (let [selected (r/atom nil)
        ;; Convert agents map to list with ids
        agent-list (map (fn [[id agent]] (assoc agent :id id)) agents)]

    (fn [{:keys [agents width height on-agent-click]}]
      (let [agent-list (map (fn [[id agent]] (assoc agent :id id)) agents)
            ;; Calculate layout
            center-x (/ (or width 800) 2)
            center-y (/ (or height 500) 2)
            positioned (layout-agents agent-list center-x center-y)
            positioned-map (into {} (map (juxt :id identity) positioned))]

        [p/graph-container
         {:width width
          :height height
          :on-background-click #(reset! selected nil)}

         ;; Legend
         [:g {:transform "translate(20, 20)"}
          [:text {:y 0 :font-size 12 :fill "#9ca3af"} "Agent Types:"]
          ;; Coordinator
          [:circle {:cx 15 :cy 20 :r 8 :fill "#ec4899"}]
          [:text {:x 30 :y 24 :font-size 11 :fill "#e5e5e5"} "Coordinator"]
          ;; Ling
          [:circle {:cx 15 :cy 45 :r 8 :fill "#8b5cf6"}]
          [:text {:x 30 :y 49 :font-size 11 :fill "#e5e5e5"} "Ling"]
          ;; Drone
          [:circle {:cx 15 :cy 70 :r 8 :fill "#f97316"}]
          [:text {:x 30 :y 74 :font-size 11 :fill "#e5e5e5"} "Drone"]]

         ;; Status legend
         [:g {:transform (str "translate(" (- (or width 800) 120) ", 20)")}
          [:text {:y 0 :font-size 12 :fill "#9ca3af"} "Status:"]
          [:circle {:cx 10 :cy 20 :r 6 :fill "#22c55e"}]
          [:text {:x 22 :y 24 :font-size 10 :fill "#e5e5e5"} "Working"]
          [:circle {:cx 10 :cy 38 :r 6 :fill "#6b7280"}]
          [:text {:x 22 :y 42 :font-size 10 :fill "#e5e5e5"} "Idle"]
          [:circle {:cx 10 :cy 56 :r 6 :fill "#ef4444"}]
          [:text {:x 22 :y 60 :font-size 10 :fill "#e5e5e5"} "Error"]]

         ;; Draw hierarchy edges (parent -> child)
         (for [agent positioned
               :when (:parent-id agent)
               :let [parent (get positioned-map (:parent-id agent))]
               :when parent]
           ^{:key (str "edge-" (:id agent))}
           [hierarchy-edge {:parent parent :child agent}])

         ;; Draw agent nodes
         (for [agent positioned]
           ^{:key (:id agent)}
           [agent-node
            {:agent agent
             :x (:x agent)
             :y (:y agent)
             :selected? (= (:id agent) @selected)
             :on-click (fn [agent-id]
                         (reset! selected agent-id)
                         (when on-agent-click
                           (on-agent-click agent-id)))}])

         ;; Selected agent details panel
         (when @selected
           (let [agent (get positioned-map @selected)
                 panel-x (- (or width 800) 280)
                 panel-y (- (or height 500) 150)]
             [:g {:class "agent-details"}
              ;; Background
              [:rect {:x panel-x :y panel-y
                      :width 260 :height 130
                      :rx 6
                      :fill "#252525"
                      :stroke "#333"
                      :stroke-width 1}]
              ;; Title
              [:text {:x (+ panel-x 10) :y (+ panel-y 25)
                      :font-size 13
                      :font-weight "bold"
                      :fill "#e5e5e5"}
               (str (agent-icon (:type agent)) " " (:id agent))]
              ;; Type
              [:text {:x (+ panel-x 10) :y (+ panel-y 50)
                      :font-size 11
                      :fill (agent-type-color (:type agent))}
               (str "Type: " (name (or (:type agent) :unknown)))]
              ;; Status
              [:text {:x (+ panel-x 10) :y (+ panel-y 70)
                      :font-size 11
                      :fill (p/status-color (:status agent))}
               (str "Status: " (name (or (:status agent) :unknown)))]
              ;; Task
              [:text {:x (+ panel-x 10) :y (+ panel-y 90)
                      :font-size 10
                      :fill "#9ca3af"}
               (str "Task: "
                    (let [task (str (:task agent))]
                      (if (> (count task) 35)
                        (str (subs task 0 32) "...")
                        (or task "None"))))]
              ;; Project
              [:text {:x (+ panel-x 10) :y (+ panel-y 110)
                      :font-size 10
                      :fill "#6b7280"}
               (str "Project: " (or (:project-id agent) "N/A"))]]))]))))

;; -- Summary Stats --

(defn agent-stats
  "Summary statistics for agents."
  []
  (let [agents @(rf/subscribe [::subs/agents])
        agent-list (vals agents)
        total (count agent-list)
        working (count (filter #(= :working (:status %)) agent-list))
        idle (count (filter #(= :idle (:status %)) agent-list))
        error (count (filter #(= :error (:status %)) agent-list))
        lings (count (filter #(= :ling (:type %)) agent-list))
        drones (count (filter #(= :drone (:type %)) agent-list))]
    [:div {:class "agent-stats"}
     [:div {:class "stat"}
      [:span {:class "stat-value"} total]
      [:span {:class "stat-label"} "Total"]]
     [:div {:class "stat"}
      [:span {:class "stat-value" :style {:color "#22c55e"}} working]
      [:span {:class "stat-label"} "Working"]]
     [:div {:class "stat"}
      [:span {:class "stat-value" :style {:color "#6b7280"}} idle]
      [:span {:class "stat-label"} "Idle"]]
     [:div {:class "stat"}
      [:span {:class "stat-value" :style {:color "#8b5cf6"}} lings]
      [:span {:class "stat-label"} "Lings"]]
     [:div {:class "stat"}
      [:span {:class "stat-value" :style {:color "#f97316"}} drones]
      [:span {:class "stat-label"} "Drones"]]]))

;; -- Integrated Agents Panel --

(defn agents-panel
  "Complete agents visualization panel with graph and stats."
  []
  (let [agents @(rf/subscribe [::subs/agents])]
    [:div {:class "panel agents-panel"}
     [:h2 "Agent Topology"]
     [agent-stats]
     [:div {:class "agent-graph-container"
            :style {:height "500px"}}
      (if (empty? agents)
        [:div {:class "empty-state"}
         [:span {:style {:font-size "2rem"}} "L"]
         [:p "No agents active"]
         [:p {:class "hint"} "Spawn lings via hivemind to see topology"]]
        [agent-topology-graph
         {:agents agents
          :width "100%"
          :height 500
          :on-agent-click #(rf/dispatch [:olympus.events/select-agent %])}])]]))
