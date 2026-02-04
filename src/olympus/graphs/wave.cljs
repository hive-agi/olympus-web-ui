(ns olympus.graphs.wave
  "Wave dispatch visualization.
   Shows drone tasks as a graph with status-colored nodes
   and execution flow edges."
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [olympus.graphs.primitives :as p]
            [olympus.subs :as subs]))

;; -- Task Status Colors --

(defn task-status-color
  "Map wave task status to color."
  [status]
  (case status
    :pending "#eab308"    ; yellow - waiting
    :running "#3b82f6"    ; blue - in progress
    :completed "#22c55e"  ; green - done successfully
    :approved "#22c55e"   ; green - diff approved
    :rejected "#ef4444"   ; red - diff rejected
    :failed "#ef4444"     ; red - execution failed
    "#6b7280"))           ; gray - unknown

(defn task-status-icon
  "Icon/emoji for task status."
  [status]
  (case status
    :pending "..."
    :running "~"
    :completed "+"
    :approved "+"
    :rejected "x"
    :failed "!"
    "?"))

;; -- Wave Task Node --

(defn wave-task-node
  "Renders a single wave task as a node.
   Props:
   - task: {:id :file :status :result}
   - x, y: position
   - selected?: highlight state
   - on-click: handler"
  [{:keys [task x y selected? on-click]}]
  (let [{:keys [id file status]} task
        ;; Extract filename from path
        filename (last (clojure.string/split (or file id) #"/"))]
    [p/node-component
     {:id id
      :x x
      :y y
      :label (task-status-icon status)
      :status status
      :radius 20
      :selected? selected?
      :on-click on-click}]))

;; -- Wave Graph --

(defn wave-graph
  "Renders a complete wave as a graph.
   Props:
   - wave: {:id :status :tasks [...]}
   - width, height: dimensions
   - on-task-click: handler"
  [{:keys [wave width height on-task-click]}]
  (let [{:keys [id status tasks]} wave
        n (count tasks)
        ;; Layout tasks in a horizontal flow
        task-width 60
        total-width (* n task-width)
        start-x (/ (- width total-width) 2)
        center-y (/ height 2)

        ;; Selected task state
        selected (r/atom nil)]

    (fn [{:keys [wave width height on-task-click]}]
      (let [{:keys [id status tasks]} wave]
        [p/graph-container
         {:width width
          :height height
          :on-background-click #(reset! selected nil)}

         ;; Wave ID label
         [:text {:x 20 :y 30
                 :font-size 14
                 :fill "#e5e5e5"}
          (str "Wave: " id)]

         ;; Status indicator
         [:text {:x 20 :y 50
                 :font-size 12
                 :fill (task-status-color status)}
          (str "Status: " (name (or status :unknown)))]

         ;; Task count
         [:text {:x 20 :y 70
                 :font-size 12
                 :fill "#9ca3af"}
          (str "Tasks: " (count tasks))]

         ;; Edges connecting sequential tasks
         (for [i (range (dec (count tasks)))]
           (let [x1 (+ start-x (* i task-width) 40)
                 x2 (+ start-x (* (inc i) task-width) 40)]
             ^{:key (str "edge-" i)}
             [p/edge-component
              {:from {:x (+ x1 20) :y center-y}
               :to {:x (- x2 20) :y center-y}
               :color "#4b5563"
               :arrow? true}]))

         ;; Task nodes
         (for [[i task] (map-indexed vector tasks)]
           (let [x (+ start-x (* i task-width) 40)]
             ^{:key (:id task)}
             [wave-task-node
              {:task task
               :x x
               :y center-y
               :selected? (= (:id task) @selected)
               :on-click (fn [task-id]
                           (reset! selected task-id)
                           (when on-task-click
                             (on-task-click task-id)))}]))

         ;; Selected task details panel
         (when @selected
           (let [task (first (filter #(= (:id %) @selected) tasks))
                 panel-x (- width 250)
                 panel-y 20]
             [:g {:class "task-details"}
              ;; Background
              [:rect {:x panel-x :y panel-y
                      :width 230 :height 120
                      :rx 6
                      :fill "#252525"
                      :stroke "#333"
                      :stroke-width 1}]
              ;; Title
              [:text {:x (+ panel-x 10) :y (+ panel-y 25)
                      :font-size 12
                      :font-weight "bold"
                      :fill "#e5e5e5"}
               "Task Details"]
              ;; File
              [:text {:x (+ panel-x 10) :y (+ panel-y 50)
                      :font-size 11
                      :fill "#9ca3af"}
               (str "File: " (or (:file task) "N/A"))]
              ;; Status
              [:text {:x (+ panel-x 10) :y (+ panel-y 70)
                      :font-size 11
                      :fill (task-status-color (:status task))}
               (str "Status: " (name (or (:status task) :unknown)))]
              ;; Result preview
              [:text {:x (+ panel-x 10) :y (+ panel-y 90)
                      :font-size 10
                      :fill "#6b7280"}
               (let [result (str (:result task))]
                 (if (> (count result) 30)
                   (str (subs result 0 27) "...")
                   result))]]))]))))

;; -- All Waves View --

(defn waves-overview
  "Overview of all active waves as mini-graphs."
  []
  (let [waves @(rf/subscribe [::subs/waves])
        selected-wave @(rf/subscribe [::subs/selected-wave])]
    [:div {:class "waves-overview"}
     (if (empty? waves)
       [:div {:class "empty-state"}
        [:span {:style {:font-size "2rem"}} "~"]
        [:p "No active waves"]]
       [:div {:class "waves-grid"}
        (for [[wave-id wave] waves]
          ^{:key wave-id}
          [:div {:class (str "wave-mini"
                             (when (= wave-id selected-wave) " selected"))
                 :on-click #(rf/dispatch [:olympus.events/select-wave wave-id])}
           [wave-graph
            {:wave (assoc wave :id wave-id)
             :width 400
             :height 200
             :on-task-click nil}]])])]))

;; -- Single Wave Detail View --

(defn wave-detail
  "Detailed view of a single wave."
  []
  (let [selected-wave @(rf/subscribe [::subs/selected-wave])
        waves @(rf/subscribe [::subs/waves])]
    (if selected-wave
      (let [wave (get waves selected-wave)]
        [:div {:class "wave-detail"}
         [:div {:class "wave-header"}
          [:h3 (str "Wave: " selected-wave)]
          [:button {:class "btn-close"
                    :on-click #(rf/dispatch [:olympus.events/select-wave nil])}
           "x"]]
         [wave-graph
          {:wave (assoc wave :id selected-wave)
           :width "100%"
           :height 400
           :on-task-click #(js/console.log "Task clicked:" %)}]])
      [:div {:class "empty-state"}
       "Select a wave to view details"])))

;; -- Integrated Waves Panel --

(defn waves-panel
  "Complete waves visualization panel.
   Shows overview grid with detail view when selected."
  []
  (let [selected-wave @(rf/subscribe [::subs/selected-wave])]
    [:div {:class "panel waves-panel"}
     [:h2 "Wave Dispatches"]
     (if selected-wave
       [wave-detail]
       [waves-overview])]))
