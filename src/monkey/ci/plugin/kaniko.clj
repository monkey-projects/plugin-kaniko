(ns monkey.ci.plugin.kaniko
  (:require [monkey.ci.build
             [api :as api]
             [core :as bc]
             [shell :as s]]))

(def kaniko-version "1.21.0")

(defn image
  "Creates a build job that builds and pushes a container image using kaniko."
  [{:keys [creds-param subdir dockerfile target-img]
    :or {creds-param "dockerhub-creds"
         dockerfile "Dockerfile"}}
   ctx]
  (let [wd (cond-> (s/container-work-dir ctx)
             subdir (str "/" subdir))
        creds (get (api/build-params ctx) creds-param)
        config-dir "/kaniko/.docker"
        config-file (str config-dir "/config.json")]
    (bc/container-job
     "image"
     {:image (str "docker.io/monkeyci/kaniko:" kaniko-version)
      :container/env {"DOCKER_CREDS" creds
                      ;; Must point to the directory where 'config.json' is in
                      "DOCKER_CONFIG" config-dir}
      ;; Kaniko requires that docker credentials are written to file
      :script [(str "echo $DOCKER_CREDS > " config-file)
               (format "/kaniko/executor --destination %s --dockerfile %s --context dir://%s"
                       target-img (str wd "/" dockerfile) wd)]})))
