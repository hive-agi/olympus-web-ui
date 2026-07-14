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
   ;; hive-mcp sends: {:type "event-name" :timestamp ... :other-data ...}
   ;; Convert type to keyword for matching
   (let [msg-type (keyword (:type message))]
     (js/console.log "Processing message type:" msg-type)
     (case msg-type
       ;; === Full snapshot on WS connect ===
       :init-snapshot
       (let [data (:data message)
             ;; Convert agents vector to {id -> agent-map} with keywordized fields
             agents-map (into {}
                              (map (fn [a]
                                     [(or (:id a) (:agent-id a))
                                      {:id (or (:id a) (:agent-id a))
                                       :type (keyword (or (:type a) :ling))
                                       :status (keyword (or (:status a) :idle))
                                       :task (:task a)
                                       :parent-id (:parent-id a)
                                       :project-id (:project-id a)
                                       :last-message (:message a)}])
                                   (:agents data)))
             ;; Waves come as {wave-id -> wave-data} map
             waves-map (into {}
                             (map (fn [[k v]]
                                    [(name k) (assoc v :id (name k)
                                                     :status (keyword (or (:status v) :unknown)))])
                                  (:waves data)))]
         (js/console.log "Loaded snapshot:" (count agents-map) "agents," (count waves-map) "waves")
         (-> db
             (assoc :agents agents-map)
             (assoc :waves waves-map)
             (assoc :kg (or (:kg data) (:kg db)))
             (assoc-in [:connection :last-snapshot] (:timestamp message))))

       ;; === Incremental state patch from DS bridge ===
       :state-patch
       (let [data (:data message)]
         (cond-> db
           (:agents data)
           (assoc :agents (into {}
                                (map (fn [a]
                                       [(or (:id a) (:agent-id a))
                                        {:id (or (:id a) (:agent-id a))
                                         :type (keyword (or (:type a) :ling))
                                         :status (keyword (or (:status a) :idle))
                                         :task (:task a)
                                         :parent-id (:parent-id a)
                                         :project-id (:project-id a)
                                         :last-message (:message a)}])
                                     (:agents data))))
           (:waves data)
           (assoc :waves (into {}
                               (map (fn [[k v]]
                                      [(name k) (assoc v :id (name k)
                                                       :status (keyword (or (:status v) :unknown)))])
                                    (:waves data))))))

       ;; === Hivemind shout event (from olympus broadcast) ===
       :hivemind-shout
       (let [agent-id (:agent-id message)
             event-type (keyword (:event-type message))]
         (-> db
             ;; Update agent status based on shout
             (cond->
              agent-id
               (-> (assoc-in [:agents agent-id :status]
                             (case event-type
                               :started :working
                               :progress :working
                               :completed :completed
                               :error :error
                               :blocked :blocked
                               :idle))
                   (assoc-in [:agents agent-id :last-message] (:message message))
                   (assoc-in [:agents agent-id :task] (or (:task message)
                                                          (get-in db [:agents agent-id :task])))))
             ;; Add to event stream
             (update :events conj {:agent-id agent-id
                                   :event-type event-type
                                   :message (:message message)
                                   :task (:task message)
                                   :timestamp (:timestamp message)})))

       ;; Agent events from hivemind shouts (legacy direct format)
       :started (-> db
                    (assoc-in [:agents (:agent-id message)]
                              {:id (:agent-id message)
                               :status :working
                               :task (:task message)
                               :type :ling})
                    (update :events conj message))
       :progress (-> db
                     (assoc-in [:agents (:agent-id message) :status] :working)
                     (assoc-in [:agents (:agent-id message) :last-message] (:message message))
                     (update :events conj message))
       :completed (-> db
                      (assoc-in [:agents (:agent-id message) :status] :completed)
                      (update :events conj message))
       :error (-> db
                  (assoc-in [:agents (:agent-id message) :status] :error)
                  (update :events conj message))
       :blocked (-> db
                    (assoc-in [:agents (:agent-id message) :status] :blocked)
                    (update :events conj message))

       ;; Agent lifecycle
       :agent-spawned (assoc-in db [:agents (:agent-id message)]
                                {:id (:agent-id message)
                                 :type (keyword (or (:type message) :ling))
                                 :status :idle
                                 :parent-id (:parent-id message)
                                 :project-id (:project-id message)})
       :agent-killed (update db :agents dissoc (:agent-id message))

       ;; Wave events
       :wave-started (assoc-in db [:waves (:wave-id message)]
                               {:id (:wave-id message)
                                :status :running
                                :tasks (:tasks message)})
       :wave-completed (assoc-in db [:waves (:wave-id message) :status] :completed)

       ;; Legacy format
       :agents (assoc db :agents (:data message))
       :wave-update (assoc-in db [:waves (:wave-id message)] (:data message))
       :hivemind-event (update db :events conj (:data message))
       :kg-snapshot (assoc db :kg (:data message))

       ;; Test event
       :test-event (do (js/console.log "Test event received!" message) db)

       ;; Unknown - log and pass through
       (do
         (js/console.log "Unknown message type:" msg-type message)
         db)))))

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
