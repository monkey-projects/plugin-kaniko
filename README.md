# MonkeyCI Kaniko Plugin

MonkeyCI plugin for building container images using [Kaniko](https://github.com/GoogleContainerTools/kaniko?tab=readme-ov-file).
You can include this plugin in your [MonkeyCI](https://www.monkeyci.com) build scripts in order to
create container images, either for a single platform or for multiple platforms.

## Usage

First include the plugin in your build `deps.edn`:
```clojure
{:deps {com.monkeyci/plugin-kaniko {:mvn/version "VERSION"}}}
```

Several functions are available that generate build jobs depending on your needs.
If you only need to build a simple image for a single architecture, you can do this:
```clojure
(require '[monkey.ci.plugin.kaniko :as pk])

;; Returns a job function that can be executed by MonkeyCI
(pk/image-job {:target-img "docker.io/target/img:tag"
               :arch :amd
	       :subdir "docker"
	       :dockerfile "Dockerfile"
	       :creds-param "docker-credentials"})
```

The `creds-param` config key is used to fetch the credentials from your build parameters.
By default this is `docker-credentials`.  The credentials should be of the format of your
standard `.docker/config.json`.  `dockerfile` is relative to your repository root directory,
and so is `subdir`, which is used as the context for building the image.  The default name
for the docker file is `Dockerfile`.

The architectures that can be specified are those that are available in MonkeyCI (e.g.
`:arm`, `:amd`).

### Multi-platform Images

Usually you will want to build multi platform images.  In Kaniko this is not possible, but
you can create and push multiple images, each for one platform, and them merge them
together using [manifest-tool](https://github.com/estesp/manifest-tool).  This tool will
push a manifest that contains the information of all platform-specific images.  This library
provides functions that create build jobs that allow you to build and push a multi-platform
image this way.  Use `multi-platform-image-job` for this.

```clojure
;; This will generate multiple jobs that can be included in your build process.
(pk/multi-platform-image
  {:target-img "docker.io/target/img:tag"
   :archs [:arm :amd]})
```

This function will generate one `image` job for each platform, that will build and push
an image to `<target-img>-<arch>`.  An extra job is added to invoke `manifest-tool` that will
push a manifest that groups all image together using `target-img`.

Additional build parameters are the same as those for `image` and `image-job` (except for `arch`).

## TODO

Make configuration more flexible by allowing to specify the job names and image templates.

## License

Copyright (c) 2024 by [Monkey Projects](https://www.monkey-projects.be)

[MIT License](LICENSE)