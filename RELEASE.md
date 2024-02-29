# How-to release

This project is using [semantic versioning](https://semver.org/) and [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/).

* create branch, do some changes
* commit messages should respect [Angular Commit Guidelines](https://github.com/angular/angular/blob/main/CONTRIBUTING.md#-commit-message-format)

## Release
### create tag and increase SNAPSHOT-number locally
```shell
./gradlew release
```

### create release on GitHub
* rebase to main branch
* release manually
* a GitHub Actions workflow will build a new Docker image