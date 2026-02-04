(ns olympus.core
  "Application entry point."
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [olympus.events :as events]
            [olympus.views :as views]
            [olympus.websocket :as ws]))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (let [root (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root)
    (rdom/render [views/main-panel] root)))

(defn init []
  (rf/dispatch-sync [::events/initialize])
  ;; Auto-connect to hive-mcp WebSocket
  ;; (ws/connect! "ws://localhost:7911/ws")
  (let [root (.getElementById js/document "app")]
    (rdom/render [views/main-panel] root)))
