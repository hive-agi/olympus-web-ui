(ns olympus.db
  "Application state database.")

(def default-db
  {:connection {:status :disconnected
                :url "ws://localhost:7911/ws"}

   ;; Agents registry
   :agents {}  ; agent-id -> {:status :idle/:working/:error, :type :ling/:drone, ...}

   ;; Hivemind event stream
   :events []  ; [{:agent-id :event-type :message :timestamp}, ...]

   ;; Knowledge graph snapshot
   :kg {:entries {}   ; entry-id -> {:content :tags :staleness ...}
        :edges []}    ; [{:from :to :relation :confidence}, ...]

   ;; UI state
   :ui {:active-panel :agents  ; :agents | :kg | :events
        :selected-agent nil}})
