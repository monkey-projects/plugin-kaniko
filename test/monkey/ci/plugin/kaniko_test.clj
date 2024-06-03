(ns monkey.ci.plugin.kaniko-test
  (:require [clojure.test :refer [deftest testing is]]
            [monkey.ci.build
             [api :as api]
             [core :as bc]]
            [monkey.ci.plugin.kaniko :as sut]))

(deftest image
  (with-redefs [api/build-params (constantly {"dockerhub-creds" "test-creds"})]
    (let [job (sut/image {} {})]
      (testing "returns container job"
        (is (bc/container-job? job)))

      (testing "uses kaniko image"
        (is (re-matches #".*kaniko.*" (:image job))))

      (testing "sets `DOCKER_CONFIG` env var"
        (is (some? (get-in job [:container/env "DOCKER_CONFIG"]))))

      (testing "fetches dockerhub-creds from params"
        (is (= "test-creds" (get-in job [:container/env "DOCKER_CREDS"])))))))
