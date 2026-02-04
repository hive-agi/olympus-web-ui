(ns olympus.events
  "re-frame event handlers."
  (:require [re-frame.core :as rf]
            [olympus.db :as db]))

;; -- Initialization --

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   db/default-db))

;; -- WebSocket Connection --

(rf/reg-event-db
 ::connection-status
 (fn [db [_ status]]
   (assoc-in db [:connection :status] status)))

(rf/reg-event-db
 ::ws-message
 (fn [db [_ message]]
   (case (:type message)
     :agents (assoc db :agents (:data message))
     :wave-update (assoc-in db [:waves (:wave-id message)] (:data message))
     :hivemind-event (update db :events conj (:data message))
     :kg-snapshot (assoc db :kg (:data message))
     db)))

;; -- UI Events --

(rf/reg-event-db
 ::set-panel
 (fn [db [_ panel]]
   (assoc-in db [:ui :active-panel] panel)))

(rf/reg-event-db
 ::select-agent
 (fn [db [_ agent-id]]
   (assoc-in db [:ui :selected-agent] agent-id)))

(rf/reg-event-db
 ::select-wave
 (fn [db [_ wave-id]]
   (assoc-in db [:ui :selected-wave] wave-id)))
