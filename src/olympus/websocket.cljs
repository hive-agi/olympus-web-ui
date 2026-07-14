(ns olympus.websocket
  "WebSocket connection to hive-mcp."
  (:require [re-frame.core :as rf]
            [olympus.events :as events]))

(defonce ws-connection (atom nil))

(defn on-message [event]
  (let [raw-data (.-data event)]
    ;; Handle ping/pong
    (when-not (= raw-data "pong")
      (try
        ;; hive-mcp sends JSON, parse it
        (let [data (js->clj (js/JSON.parse raw-data) :keywordize-keys true)]
          (js/console.log "WS received:" (clj->js data))
          (rf/dispatch [::events/ws-message data]))
        (catch js/Error e
          (js/console.warn "Failed to parse WS message:" e raw-data))))))

(defn on-open [_]
  (js/console.log "WebSocket connected to hive-mcp")
  (rf/dispatch [::events/connection-status :connected]))

(defn on-close [_]
  (js/console.log "WebSocket disconnected")
  (rf/dispatch [::events/connection-status :disconnected]))

(defn on-error [e]
  (js/console.error "WebSocket error:" e)
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
      (reset! ws-connection ws)
      (js/console.log "Connecting to:" url))
    (catch js/Error e
      (js/console.error "Failed to create WebSocket:" e)
      (rf/dispatch [::events/connection-status :error]))))

(defn disconnect! []
  (when @ws-connection
    (.close @ws-connection)
    (reset! ws-connection nil)))

(defn send! [message]
  (when (and @ws-connection
             (= 1 (.-readyState @ws-connection)))
    (.send @ws-connection (js/JSON.stringify (clj->js message)))))

;; Keepalive ping
(defn send-ping! []
  (when (and @ws-connection
             (= 1 (.-readyState @ws-connection)))
    (.send @ws-connection "ping")))
