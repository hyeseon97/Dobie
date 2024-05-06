package com.dobie.backend.domain.docker.dockerfile.service;

import com.dobie.backend.domain.docker.dockerfile.dto.DockerContainerDto;
import com.dobie.backend.exception.exception.Environment.*;
import com.dobie.backend.exception.exception.build.BackendBuildFailedException;
import com.dobie.backend.exception.exception.build.FrontendBuildFailedException;
import com.dobie.backend.exception.exception.file.SaveFileFailedException;
import com.dobie.backend.util.file.FileManager;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Service
@Log4j2
public class DockerfileServiceImpl implements DockerfileService {

    FileManager fileManager = new FileManager();

    @Override
    public void createGradleDockerfile(String projectName, String version, String path) {

        StringBuilder sb = new StringBuilder();
        sb.append("FROM openjdk:").append(version).append("-slim\n");
        sb.append("RUN apt-get update && apt-get install -y docker.io\n");
        sb.append("VOLUME /var/run/docker.sock\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY . /app\n");
        sb.append("RUN chmod +x ./gradlew\n");
        sb.append("RUN ./gradlew clean bootJar -x test\n");
        sb.append("RUN cp $(ls -t build/libs/*.jar | head -n 1) app.jar\n");
        sb.append("ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n");

//        sb.append("RUN --mount=type=cache,target=/root/.gradle ./gradlew clean build\n");
//        sb.append("ARG JAR_FILE=build/libs/*.jar\n");
//        sb.append("COPY ${JAR_FILE} app.jar\n");
//        sb.append("ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n");
        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;
        // 경로에 build.Gradle이 존재X 또는 경로 자체가 잘못되었다면 오류 발생
        checkBuildGradle(filePath);
        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new BackendBuildFailedException(e.getErrorMessage());
        }


    }

    @Override
    public void createMavenDockerfile(String projectName, String version, String path) {

        StringBuilder sb = new StringBuilder();
        sb.append("FROM openjdk:").append(version).append("-slim\n");
        sb.append("RUN apt-get update && apt-get install -y maven\n");
        sb.append("VOLUME /var/run/docker.sock\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY . /app\n");
        sb.append("RUN mvn clean package -DskipTests\n");
        sb.append("RUN cp target/*.jar app.jar\n");
        sb.append("ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n");
        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;
        checkBuildPom(filePath);
        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new BackendBuildFailedException(e.getErrorMessage());
        }

    }

    @Override
    public void createReactDockerfile(String projectName, String version, String path) {

        StringBuilder sb = new StringBuilder();
        sb.append("FROM node:").append(version).append("-alpine as build-stage\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY package*.json ./\n");
        sb.append("RUN npm install\n");
        sb.append("COPY . .\n");
        sb.append("RUN npm run build\n");
        sb.append("CMD [ \"npm\", \"start\" ]\n");
        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;
        checkBuildPackageJson(filePath);
        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new FrontendBuildFailedException(e.getErrorMessage());
        }
    }

    @Override
    public void createVueDockerfile(String projectName, String version, String path) {

        StringBuilder sb = new StringBuilder();
        sb.append("FROM node:").append(version).append("-alpine as build-stage\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY package*.json ./\n");
        sb.append("RUN npm install\n");
        sb.append("COPY . .\n");
        sb.append("RUN npm run build\n");
        sb.append("FROM node:20.11.0-alpine\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY --from=build-stage /app/dist /app\n");
        sb.append("CMD [\"npx\", \"http-server\", \"-p\", \"5173\"]\n");
        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;
        checkBuildPackageJson(filePath);
        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new FrontendBuildFailedException(e.getErrorMessage());
        }
    }

    @Override
    public void checkBuildGradle(String filepath) {
        File directory = new File(filepath); // 디렉토리 경로 지정
        File[] filesList = directory.listFiles(); // 디렉토리의 모든 파일 및 폴더 목록 얻기
        boolean correctPath = false;
        if (filesList != null) {
            for (File file : filesList) {
                if (file.getName().equals("build.gradle")) {
//                    System.out.println("Name: " + file.getName()); // 파일 또는 디렉토리 이름 출력
                    correctPath = true;
                    break;
                }
            }
            if (!correctPath) {
//                System.out.println("파일 경로에 bulid.gradle이 존재하지않습니다.");
                throw new BuildGradleNotFoundException();
            }
        } else {
//            System.out.println("파일 경로 자체가 잘못되었음.");
            throw new FilePathNotExistException();
        }
    }

    @Override
    public void checkBuildPom(String filepath) {
        File directory = new File(filepath); // 디렉토리 경로 지정
        File[] filesList = directory.listFiles(); // 디렉토리의 모든 파일 및 폴더 목록 얻기
        boolean correctPath = false;
        if (filesList != null) {
            for (File file : filesList) {
                if (file.getName().equals("pom.xml")) {
//                    System.out.println("Name: " + file.getName()); // 파일 또는 디렉토리 이름 출력
                    correctPath = true;
                    break;
                }
            }
            if (!correctPath) {
//                System.out.println("파일 경로에 pom.xml이 존재하지않습니다.");
                throw new PomXmlNotFoundException();
            }
        } else {
//            System.out.println("파일 경로 자체가 잘못되었음.");
            throw new FilePathNotExistException();
        }
    }

    @Override
    public void checkBuildPackageJson(String filepath) {
        File directory = new File(filepath); // 디렉토리 경로 지정
        File[] filesList = directory.listFiles(); // 디렉토리의 모든 파일 및 폴더 목록 얻기
        boolean correctPath = false;
        if (filesList != null) {
            for (File file : filesList) {
                if (file.getName().equals("package.json")) {
//                    System.out.println("Name: " + file.getName()); // 파일 또는 디렉토리 이름 출력
                    correctPath = true;
                    break;
                }
            }
            if (!correctPath) {
//                System.out.println("파일 경로에 pom.xml이 존재하지않습니다.");
                throw new PackageJsonNotFoundException();
            }
        } else {
//            System.out.println("파일 경로 자체가 잘못되었음.");
            throw new FilePathNotExistException();
        }
    }

    @Override
    public void dockerContainerLister() {
        StringBuilder sb = new StringBuilder();
        sb.append("docker ps");
        CommandLine commandLine = CommandLine.parse(sb.toString());

        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);

        try {
            executor.execute(commandLine);
            String dockerOutput = outputStream.toString();
            System.out.println("docker ps 결과: \n" + dockerOutput);
            List<DockerContainerDto> containers = parseDockerPsOutput(dockerOutput);
            containers.forEach(System.out::println);

        } catch (Exception e) {
            System.out.println("docker ps 명령어 실행 중 에러 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<DockerContainerDto> parseDockerPsOutput(String output) {
        List<DockerContainerDto> containers = new ArrayList<>();
        String[] lines = output.split("\n");

        // 첫 번째 줄은 헤더이므로 건너뜀
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].split("\\s{2,}");  // 두 개 이상의 공백으로 분할
            if (parts.length >= 7) {
                String containerId = parts[0];
                String image = parts[1];
                String command = parts[2];
                String created = parts[3];
                String status = parts[4];
                String ports = parts[5];
                String innerPort = splitPorts(ports,"inner");
                String outerPort = splitPorts(ports,"outer");
                String names = parts[6];
                containers.add(new DockerContainerDto(containerId, image, command, created, status, ports, innerPort, outerPort, names));
            }
        }

        return containers;
    }

    public String splitPorts(String ports,String type){
        if(type.equals("inner")) {
            // ':'을 기준으로 문자열을 나눕니다.
            String[] parts = ports.split(":");

            // 나누어진 두 번째 부분('-'를 포함)에서 다시 '-'를 기준으로 나눕니다.
            String[] subParts = parts[1].split("->");

            // 결과적으로 첫 번째 부분이 포트 번호가 됩니다.
            return subParts[0];
        }
        else if(type.equals("outer")){
            // ':'을 기준으로 문자열을 나눕니다.
            String[] parts = ports.split("->");

            // 나누어진 두 번째 부분('-'를 포함)에서 다시 '-'를 기준으로 나눕니다.
            String[] subParts = parts[1].split("/");

            // 결과적으로 첫 번째 부분이 포트 번호가 됩니다.
            return subParts[0];
        }
        else{
            throw new PortNumberNotFoundException();
        }
    }
}
