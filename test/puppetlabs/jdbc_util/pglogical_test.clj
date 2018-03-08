(ns puppetlabs.jdbc-util.pglogical-test
  (:require [clojure.test :refer :all]
            [puppetlabs.jdbc-util.pglogical :refer :all :as pglogical]))

(deftest wrap-ddl-for-pglogical-test
  (is (= (str "do 'begin perform"
              " pglogical.replicate_ddl_command(''set local search_path to public;"
              " create table test(a integer);''"
              "); end;';")
         (wrap-ddl-for-pglogical "create table test(a integer);" "public")))

  (is (= (str "do 'begin perform"
              " pglogical.replicate_ddl_command(''set local search_path to public;"
              " ALTER TABLE schema_migrations ADD COLUMN description varchar(1024);"
              " ALTER TABLE schema_migrations ADD COLUMN applied timestamp;''"
              "); end;';")
         (wrap-ddl-for-pglogical ["ALTER TABLE schema_migrations ADD COLUMN description varchar(1024)"
                                  "ALTER TABLE schema_migrations ADD COLUMN applied timestamp"] "public"))))


(deftest consolidate-replica-status-test
  (testing "when 2 subscriptions are running, returns :running"
    (is (= :running (consolidate-replica-status ["replicating" "replicating"]))))
  (testing "when one subscription is down,"
    (testing "and the rest are running, returns :down"
      (is (= :down (consolidate-replica-status ["replicating" "down"]))))
    (testing "and another is disabled, returns :down"
      (is (= :down (consolidate-replica-status ["disabled" "down"])))))
  (testing "when one subscription is disabled,"
    (testing "and the rest are running, returns :disabled"
      (is (= :disabled (consolidate-replica-status ["replicating" "disabled"])))))
  (testing "when no subscriptions are configured, returns :disabled"
    (testing "and the rest are running, returns :none"
      (is (= :none (consolidate-replica-status []))))))

(deftest consolidate-provider-status-test
  (are [statuses expected] (= expected (#'pglogical/consolidate-provider-status statuses))
    '() :none
    '(true) :active
    '(false) :inactive
    '(true true) :active
    '(true false) :inactive))

(deftest replication-status-test
  (testing "when no database connection is available"
    (with-redefs [pglogical/provider-replication-status (fn [db]
                                                          (Thread/sleep 10000))]
      (is (= :unknown (#'pglogical/replication-status {:classname "org.postgresql.Driver"
                                                       :subprotocol "postgresql"
                                                       :subname "fakedb"
                                                       :user "fakedb"
                                                       :password "fakedb"}
                                                      :source
                                                      4))))))
