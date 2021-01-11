package net.idlestate.gradle.duplicates

class FileToVersion {
        String file

        String version

        FileToVersion(String file, String version) {
            this.file = file
            this.version = version
        }

    @Override
    String toString() {
        return "file: ${file} version: ${version}"
    }
}
