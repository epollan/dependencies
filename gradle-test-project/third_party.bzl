#######################
# Generated and designed to be cat'ed into <root>/third_party.bzl
#######################

# `generated_maven_jars()` is designed to be executed this within your module's WORKSPACE file, like:
#
#     load("//:third_party.bzl", "generated_maven_jars")
#     generated_maven_jars()

def generated_maven_jars():

  native.maven_jar(
      name = "com_google_code_findbugs_jsr305",
      artifact = "com.google.code.findbugs:com.google.code.findbugs:1.3.9",
  )

  native.maven_jar(
      name = "com_google_errorprone_error_prone_annotations",
      artifact = "com.google.errorprone:com.google.errorprone:2.0.18",
  )

  native.maven_jar(
      name = "com_google_guava_guava",
      artifact = "com.google.guava:com.google.guava:23.0",
  )

  native.maven_jar(
      name = "com_google_j2objc_j2objc_annotations",
      artifact = "com.google.j2objc:com.google.j2objc:1.1",
  )

  native.maven_jar(
      name = "org_apache_commons_commons_lang3",
      artifact = "org.apache.commons:org.apache.commons:3.0.1",
  )

  native.maven_jar(
      name = "org_codehaus_mojo_animal_sniffer_annotations",
      artifact = "org.codehaus.mojo:org.codehaus.mojo:1.14",
  )

  native.maven_jar(
      name = "org_ini4j_ini4j",
      artifact = "org.ini4j:org.ini4j:0.5.2",
  )


# `generated_java_libraries()` is designed to be executed within `third_party/BUILD`
#
#     load("//:third_party.bzl", "generated_java_libraries")
#     generated_java_libraries()

def generated_java_libraries():

  native.java_library(
      name = "com_google_code_findbugs_jsr305",
      visibility = ["//visibility:public"],
      exports = ["@com_google_code_findbugs_jsr305//jar"],
      runtime_deps = [
      ]
  )

  native.java_library(
      name = "com_google_errorprone_error_prone_annotations",
      visibility = ["//visibility:public"],
      exports = ["@com_google_errorprone_error_prone_annotations//jar"],
      runtime_deps = [
      ]
  )

  native.java_library(
      name = "com_google_j2objc_j2objc_annotations",
      visibility = ["//visibility:public"],
      exports = ["@com_google_j2objc_j2objc_annotations//jar"],
      runtime_deps = [
      ]
  )

  native.java_library(
      name = "org_apache_commons_commons_lang3",
      visibility = ["//visibility:public"],
      exports = ["@org_apache_commons_commons_lang3//jar"],
      runtime_deps = [
      ]
  )

  native.java_library(
      name = "org_codehaus_mojo_animal_sniffer_annotations",
      visibility = ["//visibility:public"],
      exports = ["@org_codehaus_mojo_animal_sniffer_annotations//jar"],
      runtime_deps = [
      ]
  )

  native.java_library(
      name = "org_ini4j_ini4j",
      visibility = ["//visibility:public"],
      exports = ["@org_ini4j_ini4j//jar"],
      runtime_deps = [
      ]
  )

  native.java_library(
      name = "com_google_guava_guava",
      visibility = ["//visibility:public"],
      exports = ["@com_google_guava_guava//jar"],
      runtime_deps = [
          ":com_google_code_findbugs_jsr305",
          ":com_google_errorprone_error_prone_annotations",
          ":com_google_j2objc_j2objc_annotations",
          ":org_codehaus_mojo_animal_sniffer_annotations",
      ]
  )

