(ns monkey.ci.plugin.kaniko
  (:require [clojure.string :as cs]
            [monkey.ci.build
             [api :as api]
             [core :as bc]
             [shell :as s]]))

(def kaniko-version "1.23.2")

(defn image
  "Creates a build job that builds and pushes a container image using kaniko."
  [{:keys [creds-param subdir dockerfile target-img arch]
    :or {creds-param "dockerhub-creds"
         dockerfile "Dockerfile"}
    :as conf}
   ctx]
  (let [wd (cond-> (s/container-work-dir ctx)
             subdir (str "/" subdir))
        creds (get (api/build-params ctx) creds-param)
        config-dir "/kaniko/.docker"
        config-file (str config-dir "/config.json")]
    (bc/container-job
     (cond-> "image"
       arch (str "-" (name arch)))
     (-> {:image (str "docker.io/monkeyci/kaniko:" kaniko-version)
          :container/env {"DOCKER_CREDS" creds
                          ;; Must point to the directory where 'config.json' is in
                          "DOCKER_CONFIG" config-dir}
          ;; Kaniko requires that docker credentials are written to file
          :script [(str "echo $DOCKER_CREDS > " config-file)
                   (format "/kaniko/executor --destination %s --dockerfile %s --context dir://%s"
                           target-img (str wd "/" dockerfile) wd)]}
         (merge (select-keys conf [:arch]))))))

(defn image-job
  "Returns a job fn that pushes an image using given config"
  [conf]
  (partial image conf))

(defn manifest
  "Uses manifest-tool to merge the images built for several architectures into one
   manifest and pushes it."
  [{:keys [creds-param archs img-template target-img]
    :or {creds-param "dockerhub-creds"}
    :as conf}
   ctx]
  (let [creds-path "/tmp/docker-config.json"
        creds (get (api/build-params ctx) creds-param)]
    (bc/container-job
     "push-manifest"
     ;; TODO Switch to mplatform/manifest-tool as soon as MonkeyCI allows shell-less containers
     {:image "docker.io/monkeyci/manifest-tool:2.1.7"
      :container/env {"DOCKER_CREDS" creds}
      :script [(str "echo $DOCKER_CREDS > " creds-path)
               (format "/manifest-tool --docker-cfg=%s push from-args --platforms %s --template %s --target %s"
                       creds-path
                       (->> archs
                            (map (comp (partial format "linux/%s64") name))
                            (cs/join ","))
                       img-template
                       target-img)]
      :dependencies (mapv (comp (partial str "image-") name) archs)})))

(defn manifest-job [conf]
  (partial manifest conf))

(defn multi-platform-image
  "Creates jobs for building and pushing a multi-platform-image for the configured
   architectures and image tag."
  [{:keys [target-img archs] :as conf} ctx]
  (letfn [(img-template [arch]
            (str target-img "-" (name arch)))]
    (-> (map #(image (-> conf
                         (dissoc :archs)
                         (assoc :arch %
                                :target-img (img-template %)))
                     ctx)
             archs)
        (concat [(manifest (-> conf
                               (select-keys [:creds-param :archs :target-img])
                               (assoc :img-template (str target-img "-ARCH")))
                           ctx)]))))

(defn multi-platform-image-job [conf]
  (partial multi-platform-image conf))
