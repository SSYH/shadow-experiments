(ns shadow.experiments.grove.cards.runner
  (:require
    [shadow.experiments.grove.cards.env :as gce]
    [shadow.experiments.arborist.protocols :as ap]
    [shadow.experiments.grove :as sg :refer (<<)]
    [shadow.experiments.grove.db :as db]
    [shadow.experiments.arborist.common :as common]
    [shadow.experiments.grove.protocols :as gp]
    [shadow.experiments.arborist.attributes :as attr]
    [shadow.experiments.arborist.fragments :as frag]))

(deftype LocalQueryEngine [db-data stream-data]
  gp/IQueryEngine
  (query-init [this key query config callback]
    (let [result (db/query {} db-data query)]
      (js/console.log "query-init" key query config result)
      ;; mimic async queries for now?
      (js/setTimeout #(callback result) 0)))
  (query-destroy [this key])
  (transact! [this tx]
    (js/console.log "tx" tx))

  gp/IStreamEngine
  (stream-init [this env stream-id stream-key opts callback]
    (callback
      {:op :init
       :item-count 3
       :items []})

    (js/console.log "stream-init" env stream-id stream-key opts callback))
  (stream-destroy [this stream-id stream-key]
    (js/console.log "stream-destroy" stream-id)))

(defn test-query-engine [schema static-db static-streams]
  (let [db-data (db/configure static-db schema)]
    (fn [env]
      (assoc env ::gp/query-engine (LocalQueryEngine. db-data static-streams)))))

(defn make-card-env [{:keys [id opts] :as card}]
  (let [{:keys [schema db streams]} opts]
    (-> (sg/init* id
          {:dom/element-fn frag/dom-element-fn}
          [(test-query-engine schema db streams)]))))

(defn start []
  (let [cards
        (->> @gce/cards-ref
             (vals)
             (sort-by :id))]

    (doseq [{:keys [id rendered root managed dirty?] :as card} cards
            ;; :when dirty?
            ]
      (let [env (make-card-env card)
            wrapper (<< [:div.p-4
                         [:div.border.shadow
                          [:div.p-1.bg-gray-100 (str id)]
                          [:div.border-t {:style {:height "300px"}}
                           rendered]]])
            new (ap/as-managed wrapper env)
            root (or root (js/document.createElement "div"))]

        (if managed
          (do (common/fragment-replace managed new))
          (do (js/document.body.appendChild root)
              (ap/dom-insert new root nil)))

        (ap/dom-entered! new)
        (swap! gce/cards-ref update id merge {:root root :managed new :env env :dirty? false})
        ))))

(defn stop [done]
  (done))

(defn ^:export init []
  (js/console.log "init")
  (start))

