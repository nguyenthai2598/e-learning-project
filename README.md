```bash
 _____ _                       _           _
| ____| |      _ __  _ __ ___ (_) ___  ___| |_
|  _| | |     | '_ \| '__/ _ \| |/ _ \/ __| __|
| |___| |___  | |_) | | | (_) | |  __/ (__| |_ _
|_____|_____| | .__/|_|  \___// |\___|\___|\__(_)
              |_|           |__/
```

## Prerequisites
- Git ([How?](docs/how_to_install_git.md))
- Java 17 ([How?](docs/how_to_install_java.md))
- Docker ([How?](docs/how_to_install_docker.md))

> **Hint:** You need to have the credential of the s3 storage and the stripe then put it in the config file at backend/lms/src/main/resources/application.yml to work.

## Run application
```bash
git clone https://github.com/thnguyen101/e-learning-project.git
cd e-learning-project
chmod +x build.sh
./build.sh without-angular
```

Check it out at:
> http://**{hostname}**:7080/angular-ui/ 
