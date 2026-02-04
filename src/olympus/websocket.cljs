(ns olympus.websocket
  "WebSocket connection to hive-mcp."
  (:require [re-frame.core :as rf]
            [olympus.events :as events]
            [clojure.edn :as edn]))

(defonce ws-connection (atom nil))

(defn on-message [event]
  (let [data (edn/read-string (.-data event))]
    (rf/dispatch [::events/ws-message data])))

(defn on-open [_]
  (rf/dispatch [::events/connection-status :connected]))

(defn on-close [_]
  (rf/dispatch [::events/connection-status :disconnected]))

(defn on-error [_]
  (rf/dispatch [::events/connection-status :error]))

(defn connect! [url]
  (when @ws-connection
    (.close @ws-connection))
  (rf/dispatch [::events/connection-status :connecting])
  (try
    (let [ws (js/WebSocket. url)]
      (set! (.-onopen ws) on-open)
      (set! (.-onclose ws) on-close)
      (set! (.-onerror ws) on-error)
      (set! (.-onmessage ws) on-message)
      (reset! ws-connection ws))
    (catch js/Error e
      (rf/dispatch [::events/connection-status :error]))))

(defn disconnect! []
  (when @ws-connection
    (.close @ws-connection)
    (reset! ws-connection nil)))

(defn send! [message]
  (when (and @ws-connection
             (= 1 (.-readyState @ws-connection)))
    (.send @ws-connection (pr-str message))))
