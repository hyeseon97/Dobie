package com.dobie.backend.domain.docker.dockerfile.service;

import com.dobie.backend.domain.docker.readjson.service.ReadJsonService;
import com.dobie.backend.exception.exception.build.DjangoBuildFailedException;
import com.dobie.backend.exception.exception.build.FastApiBuildFailedException;
import com.dobie.backend.exception.exception.docker.DockerPsErrorException;
import com.dobie.backend.exception.exception.Environment.*;
import com.dobie.backend.exception.exception.build.BackendBuildFailedException;
import com.dobie.backend.exception.exception.build.FrontendBuildFailedException;
import com.dobie.backend.exception.exception.docker.DockerPsLinePartsErrorException;
import com.dobie.backend.exception.exception.file.SaveFileFailedException;
import com.dobie.backend.util.command.CommandService;
import com.dobie.backend.util.file.FileManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@Log4j2
@RequiredArgsConstructor
public class DockerfileServiceImpl implements DockerfileService {

    private final ReadJsonService readJsonService;
    private final CommandService commandService;
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

        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;

        // 이미 경로에 Dockerfile이 있다면 삭제하는 코드
        Path existDockerFile = Paths.get(filePath, "Dockerfile");
        if(Files.exists(existDockerFile)) {
            commandService.deleteFile("Dockerfile", filePath);
        }

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

        // 이미 경로에 Dockerfile이 있다면 삭제하는 코드
        Path existDockerFile = Paths.get(filePath, "Dockerfile");
        if(Files.exists(existDockerFile)) {
            commandService.deleteFile("Dockerfile", filePath);
        }

        checkBuildPom(filePath);
        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new BackendBuildFailedException(e.getErrorMessage());
        }

    }

    @Override
    public void createReactDockerfile(String projectName, String version, String path, boolean usingNginx) {

        StringBuilder sb = new StringBuilder();
        sb.append("FROM node:").append(version).append("-alpine as build-stage\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY package*.json ./\n");
        sb.append("RUN npm install\n");
        sb.append("COPY . .\n");
        sb.append("RUN npm run build\n");
        if(usingNginx) {
            sb.append("FROM nginx:alpine\n");
            sb.append("RUN rm -rf /etc/nginx/conf.d\n");
            sb.append("COPY conf /etc/nginx\n");
            sb.append("COPY --from=build-stage /app/build /usr/share/nginx/html\n");
            sb.append("EXPOSE 80\n");
            sb.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
        }else{
            sb.append("CMD [ \"npm\", \"start\" ]\n");
        }
        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;

        // 이미 경로에 Dockerfile이 있다면 삭제하는 코드
        Path existDockerFile = Paths.get(filePath, "Dockerfile");
        if(Files.exists(existDockerFile)) {
            commandService.deleteFile("Dockerfile", filePath);
        }

        checkBuildPackageJson(filePath);
        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new FrontendBuildFailedException(e.getErrorMessage());
        }
    }

    @Override
    public void createVueDockerfile(String projectName, String version, String path, int internalPort) {

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
        sb.append("CMD [\"npx\", \"http-server\", \"-p\", \"").append(internalPort).append("\"]\n");
        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;

        // 이미 경로에 Dockerfile이 있다면 삭제하는 코드
        Path existDockerFile = Paths.get(filePath, "Dockerfile");
        if(Files.exists(existDockerFile)) {
            commandService.deleteFile("Dockerfile", filePath);
        }

        checkBuildPackageJson(filePath);
        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new FrontendBuildFailedException(e.getErrorMessage());
        }
    }

    @Override
    public void createFastApiDockerfile(String projectName, String version, String path) {

        StringBuilder sb = new StringBuilder();
        sb.append("FROM python:").append(version).append("\n");
        sb.append("COPY ./requirements.txt /CATTALE/recommend-server/requirements.txt\n");
        sb.append("RUN pip install -r /CATTALE/recommend-server/requirements.txt\n");
        sb.append("COPY ./.env.dev /CATTALE/recommend-server/.env.dev\n");
        sb.append("COPY ./.env.prod /CATTALE/recommend-server/.env.prod\n");
        sb.append("COPY ./.env /CATTALE/recommend-server/.env\n");
        sb.append("COPY ./app /CATTALE/recommend-server/app\n");
        sb.append("WORKDIR /CATTALE/recommend-server/app\n");
        sb.append("CMD [\"uvicorn\", \"main:app\", \"--host\", \"0.0.0.0\", \"--port\", \"8000\"]\n");

        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;
        checkRequirementsTxt(filePath);
        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new FastApiBuildFailedException(e.getErrorMessage());
        }
    }

    @Override
    public void createDjangoDockerfile(String projectName, String version, String path, int internalPort) {

        StringBuilder sb = new StringBuilder();
        sb.append("FROM python:").append(version).append("-slim\n");
        sb.append("WORKDIR /app\n");
        sb.append("ENV PYTHONDONTWRITEBYTECODE 1\n");
        sb.append("ENV PYTHONUNBUFFERED 1\n");
        sb.append("COPY requirements.txt /app/\n");
        sb.append("RUN pip install --no-cache-dir -r requirements.txt\n");
        sb.append("COPY . /app/\n");
        sb.append("EXPOSE ").append(internalPort);
        sb.append("CMD [\"python\", \"manage.py\", \"runserver\", \"0.0.0.0:").append(internalPort).append("\"]\n");
        String dockerfile = sb.toString();

        // ec2 서버에서 깃클론하는 경로로 수정하기
        String filePath = "./" + projectName + path;

        try {
            fileManager.saveFile(filePath, "Dockerfile", dockerfile);
        } catch (SaveFileFailedException e) {
            throw new DjangoBuildFailedException(e.getErrorMessage());
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
                    correctPath = true;
                    break;
                }
            }
            if (!correctPath) {
                throw new BuildGradleNotFoundException();
            }
        } else {
            throw new BackendFilePathNotExistException();
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
                    correctPath = true;
                    break;
                }
            }
            if (!correctPath) {
                throw new PomXmlNotFoundException();
            }
        } else {
            throw new BackendFilePathNotExistException();
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
                    correctPath = true;
                    break;
                }
            }
            if (!correctPath) {
                throw new PackageJsonNotFoundException();
            }
        } else {
            throw new FrontendFilePathNotExistException();
        }
    }

    @Override
    public void checkRequirementsTxt(String filepath) {
        File directory = new File(filepath); // 디렉토리 경로 지정
        File[] filesList = directory.listFiles(); // 디렉토리의 모든 파일 및 폴더 목록 얻기
        boolean correctPath = false;
        if (filesList != null) {
            for (File file : filesList) {
                if (file.getName().equals("requirements.txt")) {
                    correctPath = true;
                    break;
                }
            }
            if (!correctPath) {
                throw new RequirementsTxtNotFoundException();
            }
        } else {
            throw new FastApiFilePathNotExistException();
        }
    }

    @Override
    public HashMap<String,String> dockerContainerLister(String projectId) {
        try{
            ArrayList<String> analyzeList = AnalyzeProjectContainer(projectId);
            String dockerOutput = readProceedingDockerContainer();
            HashMap<String,String> containers = parseDockerPsOutput(dockerOutput);
            HashMap<String,String> analyzeContainer = new HashMap<>();
            boolean allRunning = false;
            for(int i=0; i<analyzeList.size(); i++){
                String currentContainerName = analyzeList.get(i);
                String currentStatus = containers.get(currentContainerName);
                if(currentStatus==null){
                    analyzeContainer.put(currentContainerName,"ERROR :/");
                }else {
                    analyzeContainer.put(currentContainerName, currentStatus);
                   if(currentStatus.equals("Running :)")&&!allRunning){
                       allRunning = true;
                   }
                }
            }
            if(allRunning==true){
                analyzeContainer.put("allRunning","Run");
            }else{
                analyzeContainer.put("allRunning","Stop");
            }
            return analyzeContainer;

        } catch (Exception e) {
            System.out.println("docker ps 명령어 실행 중 에러 발생: " + e.getMessage());
            e.printStackTrace();
            throw new DockerPsErrorException();
        }
    }

    @Override
    public String checkDBContainer(String projectId) {
        try {
            ArrayList<String> analyzeList = AnalyzeProjectContainer(projectId);
            HashMap<String,String> frameworkMap = AnalyzeProjectContainerFramework(projectId); //key : 컨테이너 서비스id, value : 프레임 워크
            String dockerOutput = readProceedingDockerContainer();
            HashMap<String,String> containerStatus = parseDockerPsOutput(dockerOutput); //key : 컨테이너 서비스id, value : 실행 상태

            boolean mysqlOn = false;
            boolean redisOn = false;
            boolean mongodbOn = false;
            for(int i=0; i<analyzeList.size(); i++){
                String currentContainerName = analyzeList.get(i);
                String currentStatus = containerStatus.get(currentContainerName);
                String currentFramework = frameworkMap.get(currentContainerName);
                System.out.println("현재 실행중인 컨테이너 이름 : " + currentContainerName);
                System.out.println("현재 실행중인 컨테이너 상태 : " + currentStatus);
                System.out.println("현재 실행중인 컨테이너 프레임워크 : " + currentFramework);
                if(((currentStatus==null)||(currentStatus.equals("Stopped :(")))&&(currentFramework.equals("Mysql"))&&(!mysqlOn)){
                    mysqlOn=true;
                }else if(((currentStatus==null)||(currentStatus.equals("Stopped :(")))&&(currentFramework.equals("Redis"))&&(!redisOn)){
                    redisOn=true;
                }else if(((currentStatus==null)||(currentStatus.equals("Stopped :(")))&&(currentFramework.equals("Mongodb"))&&(!mongodbOn)){
                    mongodbOn=true;
                }
            }

            StringBuilder errorContainer = new StringBuilder();
            if(mysqlOn) {
                errorContainer.append("Mysql").append(" , ");
            }
            if(redisOn){
                errorContainer.append("Redis").append(" , ");
            }
            if(mongodbOn){
                errorContainer.append("Mongodb").append(" , ");
            }
            if(mysqlOn||redisOn||mongodbOn){
                errorContainer.delete(errorContainer.length()-3,errorContainer.length());
                errorContainer.append(" 컨테이너가 종료되어있어 실행할 수 없습니다.");
            }
            if(errorContainer.toString().equals("")){
                return "Pass";
            }else{
                return errorContainer.toString();
            }
        }
        catch (Exception e){
            e.printStackTrace();
            throw new DockerContainerFrameworkErrorException();
        }
    }

    @Override
    public String checkBackendContainer(String projectId) {
        try {
            ArrayList<String> analyzeList = AnalyzeProjectContainer(projectId);
            HashMap<String,String> frameworkMap = AnalyzeProjectContainerFramework(projectId); //key : 컨테이너 서비스id, value : 프레임 워크
            String dockerOutput = readProceedingDockerContainer();
            HashMap<String,String> containerStatus = parseDockerPsOutput(dockerOutput); //key : 컨테이너 서비스id, value : 실행 상태

            boolean gradleOn = false;
            boolean mavenOn = false;
            boolean djangoOn = false;
            for(int i=0; i<analyzeList.size(); i++){
                String currentContainerName = analyzeList.get(i);
                String currentStatus = containerStatus.get(currentContainerName);
                String currentFramework = frameworkMap.get(currentContainerName);
                System.out.println("현재 실행중인 컨테이너 이름 : " + currentContainerName);
                System.out.println("현재 실행중인 컨테이너 상태 : " + currentStatus);
                System.out.println("현재 실행중인 컨테이너 프레임워크 : " + currentFramework);
                if((currentStatus.equals("Running :)"))&&(currentFramework.equals("SpringBoot(gradle)"))&&(!gradleOn)){
                    gradleOn=true;
                }else if((currentStatus.equals("Running :)"))&&(currentFramework.equals("SpringBoot(maven)"))&&(!mavenOn)){
                    mavenOn=true;
                }else if((currentStatus.equals("Running :)"))&&(currentFramework.equals("Django"))&&(!djangoOn)){
                    djangoOn=true;
                }
            }

            StringBuilder errorContainer = new StringBuilder();
            if(gradleOn) {
                errorContainer.append("SpringBoot(gradle)").append(" , ");
            }
            if(mavenOn){
                errorContainer.append("SpringBoot(maven)").append(" , ");
            }
            if(djangoOn){
                errorContainer.append("Django").append(" , ");
            }
            if(gradleOn||mavenOn||djangoOn){
                errorContainer.delete(errorContainer.length()-3,errorContainer.length());
                errorContainer.append(" 컨테이너가 실행되고있어 종료할 수 없습니다.");
            }
            if(errorContainer.toString().equals("")){
                return "Pass";
            }else{
                return errorContainer.toString();
            }
        }
        catch (Exception e){
            e.printStackTrace();
            throw new DockerContainerFrameworkErrorException();
        }
    }


    @Override
    public String makeDockerfilePathContent(String projectId, String serviceId, String type) {
        if(type.equals("Backend")) {
            try {
            //project.json을 불러오는 메소드 -> readJsonService.JsonToMap()
            //data/projectJson을 map으로 변환해서 불러왔음
            Map<String, Object> projectJsonMap = ReadJsonFromDocker();
            String projectName = (String) readJsonService.JsonGetTwo(projectJsonMap, projectId, "projectName");
            String path = (String) readJsonService.JsonGetFour(projectJsonMap, projectId, "backendMap", serviceId, "path");
            String filepath = "/" + projectName + path;
                return filepath;
            }catch (Exception e) {
                System.out.println("백엔드 도커 파일 경로 생성 오류: " + e.getMessage());
                throw new makeDockerfilePathContentException();
            }
        }else if(type.equals("Frontend")){
            try {
            //project.json을 불러오는 메소드 -> readJsonService.JsonToMap()
            //data/projectJson을 map으로 변환해서 불러왔음
            Map<String, Object> projectJsonMap = ReadJsonFromDocker();
            String projectName = (String) readJsonService.JsonGetTwo(projectJsonMap, projectId, "projectName");
            String path = (String) readJsonService.JsonGetThree(projectJsonMap, projectId, "frontend","path");
            String filepath = "/" + projectName + path;
                return filepath;
            }catch (Exception e) {
                System.out.println("프론트엔드 도커 파일 경로 생성 오류: " + e.getMessage());
                throw new makeDockerfilePathContentException();
            }
        }else{
            throw new TypeErrorException();
        }
    }

    @Override
    public String makeDockerComposefilePathContent(String projectId) {
        try {
            //project.json을 불러오는 메소드 -> readJsonService.JsonToMap()
            //data/projectJson을 map으로 변환해서 불러왔음
            Map<String, Object> projectJsonMap = ReadJsonFromDocker();
            String projectName = (String) readJsonService.JsonGetTwo(projectJsonMap, projectId, "projectName");
            String filepath = "/" + projectName;
            return filepath;
        }catch (Exception e) {
            System.out.println("도커 컴포즈 파일 경로 생성 오류 : " + e.getMessage());
            e.printStackTrace();
            throw new makeDockerComposefilePathContentException();
        }
    }

    @Override
    public String readEnvironmentDockerFile(String filepath) {

        CommandLine commandLine = new CommandLine("docker");
        commandLine.addArgument("exec");
        commandLine.addArgument("dobie-be"); //dobie-be 컨테이너에 접속하는건 고정(만약 나중에 명칭 바뀌면 바꿔줘야함)
        commandLine.addArgument("cat");
        commandLine.addArgument(filepath+"/Dockerfile"); //이렇게하면 아마 될걸..? 컨테이너에 ko2sist도비 말고 다른 컨테이너도 빌드해야 테스트 가능 ㅠ

        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);

        try {
            executor.execute(commandLine);
            String fileContent = outputStream.toString();
            return fileContent;
        } catch (Exception e) {
            System.err.println("도커 파일 내용 조회 오류 : " + e.getMessage());
            e.printStackTrace();
            throw new DockerFileContentNotFoundException();
        }
    }

    @Override
    public String readEnvironmentDockerComposeFile(String filepath) {
        CommandLine commandLine = new CommandLine("docker");
        commandLine.addArgument("exec");
        commandLine.addArgument("dobie-be"); //dobie-be 컨테이너에 접속하는건 고정(만약 나중에 명칭 바뀌면 바꿔줘야함)
        commandLine.addArgument("cat");
        commandLine.addArgument(filepath+"/docker-compose.yml"); //이렇게하면 아마 될걸..? 컨테이너에 ko2sist도비 말고 다른 컨테이너도 빌드해야 테스트 가능 ㅠ

        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);

        try {
            executor.execute(commandLine);
            String fileContent = outputStream.toString();
            return fileContent;
        } catch (Exception e) {
            System.err.println("도커 컴포즈 파일 내용 조회 오류 : " + e.getMessage());
            e.printStackTrace();
            throw new DockerComposeFileContentNotFoundException();
        }
    }

    @Override
    public String readContainerLog(String mountId) {
        CommandLine commandLine = new CommandLine("docker");
        commandLine.addArgument("logs");
        commandLine.addArgument(mountId); //serviceId 또는 databaseId

        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);

        try {
            executor.execute(commandLine);
            String logContent = processBackspaces(removeAnsiEscapeCodes(outputStream.toString()));
            return logContent;
        } catch (Exception e) {
            System.err.println("컨테이너 로그 조회 오류 : " + e.getMessage());
            e.printStackTrace();
            throw new ContainerLogNotFoundException();
        }
    }


//----------------------------------------------------------------------------------------------------
    public String readProceedingDockerContainer() throws IOException {
        CommandLine commandLine = new CommandLine("docker");
        commandLine.addArgument("ps");
        commandLine.addArgument("-a"); // "-a" 옵션 추가
        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);

        executor.execute(commandLine);
        String dockerOutput = outputStream.toString();
        return dockerOutput;
    }

    public HashMap<String,String> parseDockerPsOutput(String output) {//실행중인 컨테이너 status 조회시 사용
        HashMap<String,String> containers = new HashMap<>();
        String[] lines = output.split("\n");
        try {
            // 첫 번째 줄은 헤더이므로 건너뜀
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].split("\\s{2,}");  // 두 개 이상의 공백으로 분할
                if (parts.length == 7) {
                    String containerId = parts[0];
                    String image = parts[1];
                    String command = parts[2];
                    String created = parts[3];
                    String status = parts[4];
                    String currentStatus = checkStatus(status);
                    String ports = parts[5];
                    String innerPort = splitPorts(ports, "inner");
                    String outerPort = splitPorts(ports, "outer");
                    String names = parts[6];
                    containers.put(names, currentStatus);
                } else {
                    String containerId = parts[0];
                    String image = parts[1];
                    String command = parts[2];
                    String created = parts[3];
                    String status = parts[4];
                    String currentStatus = checkStatus(status);
                    String names = parts[5];
                    containers.put(names, currentStatus);
                }

            }
        }catch(Exception e){
            throw new DockerPsLinePartsErrorException();
        }


        return containers;
    }

    public HashMap<String,String> parseDockerPsOutputOnlyName(String output) {//실행중인 컨테이너 이름 조회시 사용
        HashMap<String,String> containers = new HashMap<>();
        String[] lines = output.split("\n");
        try {
            // 첫 번째 줄은 헤더이므로 건너뜀
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].split("\\s{2,}");  // 두 개 이상의 공백으로 분할
                if (parts.length == 7) {
                    String containerId = parts[0];
                    String image = parts[1];
                    String command = parts[2];
                    String created = parts[3];
                    String status = parts[4];
                    String currentStatus = checkStatus(status);
                    String ports = parts[5];
                    String innerPort = splitPorts(ports, "inner");
                    String outerPort = splitPorts(ports, "outer");
                    String names = parts[6];
                    containers.put(names, currentStatus);
                } else {
                    String containerId = parts[0];
                    String image = parts[1];
                    String command = parts[2];
                    String created = parts[3];
                    String status = parts[4];
                    String currentStatus = checkStatus(status);
                    String names = parts[5];
                    containers.put(names, currentStatus);
                }

            }
        }catch(Exception e){
            throw new DockerPsLinePartsErrorException();
        }


        return containers;
    }


    public String checkStatus(String status){//실행중인 컨테이너 상태 반환
        if(status.contains("Up")){
            return "Running :)";
        }else if(status.contains("Exited")){
            return "Stopped :(";
        }else if(status.contains("Created")){
            return "Created :|";
        }else{
            throw new CurrentStatusNotFoundException();
        }
    }
    public String splitPorts(String ports,String type){//실행중인 컨테이너 조회시 사용
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

    String removeAnsiEscapeCodes(String text) {//컨테이너 로그 가져올때 로그 언어 삭제 시키는 메소드
        String withoutAnsiColors = text.replaceAll("\u001B\\[[;\\d]*m", "");
        String withoutTerminalTitle = withoutAnsiColors.replaceAll("\u001B\\]0;.*?\u0007", "");
        return withoutTerminalTitle.replaceAll("\u001B\\[\\?\\d{4}[hl]", "");
    }

    String processBackspaces(String text) { //컨테이너 로그 가져올때 백스페이스 삭제 시키는 메소드
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\b') {
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    ArrayList<String> AnalyzeProjectContainer(String projectId){//프로젝트가 가지고있는 백,프론트엔드,데이터베이스의 아이디를 가져옴
        try {
            ArrayList<String> result = new ArrayList<>();
            ReadJsonFromDocker();
            //project.json을 불러오는 메소드 -> readJsonService.JsonToMap()
            //data/projectJson을 map으로 변환해서 불러왔음
            Map<String, Object> projectJsonMap = ReadJsonFromDocker();
            Map<String, Object> backendMap = (Map<String, Object>) readJsonService.JsonGetTwo(projectJsonMap, projectId, "backendMap");
            String frontendId = (String) readJsonService.JsonGetThree(projectJsonMap, projectId, "frontend", "serviceId");
            Map<String, Object> databaseMap = (Map<String, Object>) readJsonService.JsonGetTwo(projectJsonMap, projectId, "databaseMap");
            if(backendMap!=null) {
                result.addAll(backendMap.keySet());
            }
            if(frontendId!=null) {
                result.add(frontendId);
            }
            if(databaseMap!=null) {
                result.addAll(databaseMap.keySet());
            }
            return result;
        }catch (Exception e){
            e.printStackTrace();
            throw new AnalyzeProjectContainerErrorException();
        }
    }

    HashMap<String,String> AnalyzeProjectContainerFramework(String projectId){//프로젝트가 가지고있는 백,프론트엔드,데이터베이스의 아이디를 가져옴
        try {
            //project.json을 불러오는 메소드 -> readJsonService.JsonToMap()
            //data/projectJson을 map으로 변환해서 불러왔음
            Map<String, Object> projectJsonMap = ReadJsonFromDocker();
            Map<String, Object> backendMap = (Map<String, Object>) readJsonService.JsonGetTwo(projectJsonMap, projectId, "backendMap");
            String frontendId = (String) readJsonService.JsonGetThree(projectJsonMap, projectId, "frontend", "serviceId");
            Map<String, Object> databaseMap = (Map<String, Object>) readJsonService.JsonGetTwo(projectJsonMap, projectId, "databaseMap");
            HashMap<String,String> result = new HashMap<>();
            if(backendMap!=null) {
                for (String key : backendMap.keySet()) {
                    String framework = (String) readJsonService.JsonGetFour(projectJsonMap, projectId, "backendMap", key, "framework");
                    System.out.println("백엔드 컨테이너 명 : " + key + " 프레임워크 명 : " + framework);
                    result.put(key, framework);
                }
            }
            if(frontendId!=null) {
                String framework = (String) readJsonService.JsonGetThree(projectJsonMap, projectId, "frontend", "framework");
                System.out.println("프론트엔드 컨테이너 명 : " + frontendId + " 프레임워크 명 : " + framework);
                result.put(frontendId, framework);
            }
            if(databaseMap!=null) {
                for (String key : databaseMap.keySet()) {
                    String framework = (String) readJsonService.JsonGetFour(projectJsonMap, projectId, "databaseMap", key, "databaseType");
                    System.out.println("DB 컨테이너 명 : " + key + " 프레임워크 명 : " + framework);
                    result.put(key, framework);
                }
            }

            return result;
        }catch (Exception e){
            e.printStackTrace();
            throw new AnalyzeProjectContainerErrorException();
        }
    }

    //도커에서 가져온 json파일을 map으로 변환
    Map<String, Object> ReadJsonFromDocker(){
        try {
            String jsonContent = getJsonContentFromDocker("dobie-be", "/data/project.json");
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(jsonContent, Map.class);
            return jsonMap;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //도커에서 json파일 가져오기
    String getJsonContentFromDocker(String containerName, String filePath) throws IOException {
        CommandLine cmdLine = new CommandLine("docker");
        cmdLine.addArgument("exec");
        cmdLine.addArgument(containerName);
        cmdLine.addArgument("cat");
        cmdLine.addArgument(filePath);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);
        executor.execute(cmdLine);

        return outputStream.toString();
    }

}
