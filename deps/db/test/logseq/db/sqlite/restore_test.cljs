(ns logseq.db.sqlite.restore-test
  (:require [cljs.test :refer [deftest async use-fixtures is testing]]
            ["fs" :as fs]
            ["path" :as node-path]
            [datascript.core :as d]
            [logseq.db.sqlite.db :as sqlite-db]
            [logseq.db.sqlite.restore :as sqlite-restore]))

(use-fixtures
  :each
 ;; Cleaning tmp/ before leaves last tmp/ after a test run for dev and debugging
  {:before
   #(async done
           (if (fs/existsSync "tmp")
             (fs/rm "tmp" #js {:recursive true} (fn [err]
                                                  (when err (js/console.log err))
                                                  (done)))
             (done)))})

(defn- create-graph-dir
  [dir db-name]
  (fs/mkdirSync (node-path/join dir db-name) #js {:recursive true}))

(deftest restore-initial-data
  (testing "Restore a journal page with its block"
    (create-graph-dir "tmp/graphs" "test-db")
    (sqlite-db/open-db! "tmp/graphs" "test-db")
    (let [page-uuid (random-uuid)
          block-uuid (random-uuid)
          created-at (js/Date.now)
          frontend-blocks [{:db/id 100001
                            :block/uuid page-uuid
                            :block/journal-day 20230629
                            :block/name "jun 29th, 2023"
                            :block/created-at created-at
                            :block/updated-at created-at}
                           {:db/id 100002
                            :block/content "test"
                            :block/uuid block-uuid
                            :block/page {:db/id 100001}
                            :block/created-at created-at
                            :block/updated-at created-at}]
          _ (sqlite-db/transact! "test-db" frontend-blocks {})
          conn (-> (sqlite-db/get-initial-data "test-db")
                   sqlite-restore/restore-initial-data)]
      (is (= (map #(dissoc % :page_uuid) frontend-blocks)
             (->> (d/q '[:find (pull ?b [*])
                         :where [?b :block/created-at]]
                       @conn)
                  (map first)))
          "Datascript db matches data inserted into sqlite from simulated frontend"))))