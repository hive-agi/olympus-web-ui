(ns olympus.views
  "UI components."
  (:require [re-frame.core :as rf]
            [olympus.subs :as subs]
            [olympus.events :as events]
            [olympus.graphs.agent :as agent-graph]
            [olympus.graphs.wave :as wave-graph]
            [olympus.graphs.kg :as kg-graph]))

;; -- Helpers --

(defn status-color [status]
  (case status
    :idle "#6b7280"
    :working "#22c55e"
    :error "#ef4444"
    :spawning "#eab308"
    "#9ca3af"))

;; -- Navigation --

(defn nav-bar []
  (let [active-panel @(rf/subscribe [::subs/active-panel])
        connection @(rf/subscribe [::subs/connection-status])]
    [:nav {:class "nav-bar"}
     [:div {:class "nav-brand"}
      [:span {:class "brand-icon"} "🐝"]
      [:span {:class "brand-text"} "Olympus"]]
     [:div {:class "nav-tabs"}
      (for [panel [:agents :waves :kg :events]]
        ^{:key panel}
        [:button {:class (str "nav-tab" (when (= panel active-panel) " active"))
                  :on-click #(rf/dispatch [::events/set-panel panel])}
         (name panel)])]
     [:div {:class "nav-status"}
      [:span {:class (str "status-dot " (name connection))}]
      (name connection)]]))

;; -- Agents Panel --

(defn agent-card [[agent-id agent]]
  [:div {:class "agent-card"
         :style {:border-left (str "4px solid " (status-color (:status agent)))}
         :on-click #(rf/dispatch [::events/select-agent agent-id])}
   [:div {:class "agent-header"}
    [:span {:class "agent-type"} (if (= :ling (:type agent)) "🦎" "🤖")]
    [:span {:class "agent-id"} (name agent-id)]]
   [:div {:class "agent-status"} (name (:status agent))]
   (when (:task agent)
     [:div {:class "agent-task"} (:task agent)])])

(defn agents-panel []
  ;; Use the graph-based agent topology view
  [agent-graph/agents-panel])

;; -- Waves Panel --

(defn waves-panel []
  ;; Use the graph-based wave visualization
  [wave-graph/waves-panel])

;; -- Knowledge Graph Panel --

(defn kg-panel []
  ;; Use the graph-based KG visualization
  [kg-graph/kg-panel])

;; -- Events Panel --

(defn event-item [{:keys [agent-id event-type message timestamp]}]
  [:div {:class "event-item"}
   [:span {:class "event-agent"} agent-id]
   [:span {:class (str "event-type " (name event-type))} (name event-type)]
   [:span {:class "event-message"} message]])

(defn events-panel []
  (let [events @(rf/subscribe [::subs/events])]
    [:div {:class "panel events-panel"}
     [:h2 "Hivemind Events"]
     (if (empty? events)
       [:div {:class "empty-state"} "No events yet"]
       [:div {:class "events-stream"}
        (for [[idx event] (map-indexed vector events)]
          ^{:key idx}
          [event-item event])])]))

;; -- Main App --

(defn main-panel []
  (let [active-panel @(rf/subscribe [::subs/active-panel])]
    [:div {:class "app"}
     [nav-bar]
     [:main {:class "main-content"}
      (case active-panel
        :agents [agents-panel]
        :waves [waves-panel]
        :kg [kg-panel]
        :events [events-panel]
        [agents-panel])]]))
