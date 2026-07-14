(ns olympus.core
  "Application entry point.
   Uses Reagent 2.0.1 reagent.dom.client API for React 19 createRoot."
  (:require [reagent.dom.client :as rdc]
            [re-frame.core :as rf]
            [olympus.events :as events]
            [olympus.views :as views]
            [olympus.websocket :as ws]))

;; React 19 root — created once, reused on hot-reload
(defonce root (atom nil))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (when-let [r @root]
    (rdc/render r [views/main-panel])))

(defn init []
  (rf/dispatch-sync [::events/initialize])
  ;; Auto-connect to hive-mcp WebSocket
  (ws/connect! "ws://localhost:7911")
  (let [root-el (.getElementById js/document "app")]
    (reset! root (rdc/create-root root-el))
    (rdc/render @root [views/main-panel])))
