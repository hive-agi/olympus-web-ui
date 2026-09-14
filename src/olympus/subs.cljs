(ns olympus.subs
  "re-frame subscriptions."
  (:require [re-frame.core :as rf]))

;; -- Connection --

(rf/reg-sub
 ::connection-status
 (fn [db _]
   (get-in db [:connection :status])))

;; -- Agents --

(rf/reg-sub
 ::agents
 (fn [db _]
   (:agents db)))

(rf/reg-sub
 ::agents-by-status
 :<- [::agents]
 (fn [agents [_ status]]
   (filter #(= status (:status (val %))) agents)))

(rf/reg-sub
 ::selected-agent
 (fn [db _]
   (get-in db [:ui :selected-agent])))

;; -- Events --

(rf/reg-sub
 ::events
 (fn [db _]
   (take 100 (reverse (:events db)))))

;; -- Knowledge Graph --

(rf/reg-sub
 ::kg-entries
 (fn [db _]
   (get-in db [:kg :entries])))

(rf/reg-sub
 ::kg-edges
 (fn [db _]
   (get-in db [:kg :edges])))

;; -- UI --

(rf/reg-sub
 ::active-panel
 (fn [db _]
   (get-in db [:ui :active-panel])))
