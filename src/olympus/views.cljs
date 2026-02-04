(ns olympus.views
  "UI components."
  (:require [re-frame.core :as rf]
            [olympus.subs :as subs]
            [olympus.events :as events]))

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
  (let [agents @(rf/subscribe [::subs/agents])]
    [:div {:class "panel agents-panel"}
     [:h2 "Agents"]
     (if (empty? agents)
       [:div {:class "empty-state"} "No agents active"]
       [:div {:class "agents-grid"}
        (for [agent agents]
          ^{:key (first agent)}
          [agent-card agent])])]))

;; -- Waves Panel --

(defn waves-panel []
  (let [waves @(rf/subscribe [::subs/waves])]
    [:div {:class "panel waves-panel"}
     [:h2 "Waves"]
     (if (empty? waves)
       [:div {:class "empty-state"} "No active waves"]
       [:div {:class "waves-list"}
        (for [[wave-id wave] waves]
          ^{:key wave-id}
          [:div {:class "wave-card"}
           [:div {:class "wave-header"} wave-id]
           [:div {:class "wave-tasks"}
            (str (count (:tasks wave)) " tasks")]])])]))

;; -- Knowledge Graph Panel --

(defn kg-panel []
  (let [entries @(rf/subscribe [::subs/kg-entries])
        edges @(rf/subscribe [::subs/kg-edges])]
    [:div {:class "panel kg-panel"}
     [:h2 "Knowledge Graph"]
     [:div {:class "kg-stats"}
      [:span (str (count entries) " entries")]
      [:span (str (count edges) " edges")]]
     [:div {:class "kg-placeholder"}
      "Graph visualization coming soon..."]]))

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
