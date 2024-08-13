(ns monkey.ci.plugin.kaniko-test
  (:require [clojure.test :refer [deftest testing is]]
            [monkey.ci.build
             [api :as api]
             [core :as bc]]
            [monkey.ci.plugin.kaniko :as sut]))

(def test-ctx {})

(deftest image
  (with-redefs [api/build-params (constantly {"dockerhub-creds" "test-creds"})]
    (let [job (sut/image {} test-ctx)]
      (testing "returns container job"
        (is (bc/container-job? job)))

      (testing "uses kaniko image"
        (is (re-matches #".*kaniko.*" (:image job))))

      (testing "sets `DOCKER_CONFIG` env var"
        (is (some? (get-in job [:container/env "DOCKER_CONFIG"]))))

      (testing "fetches dockerhub-creds from params"
        (is (= "test-creds" (get-in job [:container/env "DOCKER_CREDS"])))))

    (testing "can specify architecture"
      (is (= ::test-arch (:arch (sut/image {:arch ::test-arch} test-ctx)))))

    (testing "includes architecture in job name"
      (is (= "image-arm" (bc/job-id (sut/image {:arch :arm} test-ctx)))))))

(deftest image-job
  (testing "returns image fn"
    (is (fn? (sut/image-job {})))))

(deftest manifest
  (with-redefs [api/build-params (constantly {"dockerhub-creds" "test-creds"})]
    (let [job (sut/manifest {:archs [:arm :amd]
                             :img-template "src-image-ARCH"
                             :target-img "dest-image"}
                            test-ctx)]
      (testing "returns container job"
        (is (bc/container-job? job)))

      (testing "uses manifest-tool image"
        (is (re-matches #".*manifest-tool.*" (:image job))))

      (testing "fetches dockerhub-creds from params"
        (is (= "test-creds" (get-in job [:container/env "DOCKER_CREDS"]))))

      (testing "invokes manifest tool with push args"
        (is (= "/manifest-tool --docker-cfg=/tmp/docker-config.json push from-args --platforms linux/arm64,linux/amd64 --template src-image-ARCH --target dest-image"
               (last (:script job)))))

      (testing "is dependent on image jobs"
        (is (= ["image-arm" "image-amd"]
               (:dependencies job)))))))

(deftest manifest-job
  (testing "returns manifest fn"
    (is (fn? (sut/manifest-job {})))))

(deftest multi-platform-image
  (with-redefs [api/build-params (constantly {"dockerhub-creds" "test-creds"})]
    (let [jobs (sut/multi-platform-image
                {:archs [:arm :amd]
                 :target-img "test/image:tag"}
                test-ctx)
          jobs-by-id (group-by bc/job-id jobs)
          match-cmdline (fn [job pattern]
                          (->> (get jobs-by-id job)
                               first
                               :script
                               last
                               (re-matches pattern)
                               rest))]
      (testing "creates image job for each platform"
        (is (= ["image-arm" "image-amd"]
               (->> jobs-by-id
                    (keys)
                    (filter (partial re-matches #"image-.*"))))))

      (testing "uses architecture in target image name"
        (is (= "test/image:tag-arm"
               (first (match-cmdline "image-arm" #".*--destination (\S+).*")))))

      (testing "creates manifest job"
        (is (contains? jobs-by-id "push-manifest")))

      (testing "pushes manifest to target job"
        (is (= "test/image:tag"
               (first (match-cmdline "push-manifest" #".*--target (\S+).*"))))))))
