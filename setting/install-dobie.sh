#!/bin/bash

# 스크립트 실행 시 발생할 수 있는 모든 에러를 처리합니다.
set -e

# 1. Docker 설치
# Docker가 이미 설치되어 있지 않은 경우 설치합니다.

for pkg in docker.io docker-doc docker-compose docker-compose-v2 containerd runc; do sudo apt-get remove $pkg; done

sudo apt-get update -y
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Docker의 공식 APT repository를 시스템에 추가합니다.
echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Docker 관련 패키지를 설치합니다.
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 2. Let's Encrypt 설치
# Let's Encrypt 설치 여부 확인
if dpkg -l | grep -q letsencrypt; then
    # 이미 Let's Encrypt가 설치되어 있는 경우, 삭제
    echo "Let's Encrypt를 삭제합니다..."
    sudo apt-get remove letsencrypt -y
fi

# Let's Encrypt 설치
echo "Let's Encrypt를 설치합니다..."
sudo apt-get update
sudo apt-get install letsencrypt -y

# 설치 확인
if [ -x "$(command -v letsencrypt)" ]; then
    echo "Let's Encrypt 설치가 완료되었습니다."
else
    echo "Let's Encrypt 설치에 실패했습니다."
fi



# 3. nginx config 폴더 생성
# /var/dobie/nginx 폴더가 없으면 생성합니다.
if [ ! -d "/var/dobie/nginx" ]; then
    echo "nginx 폴더가 없어서 새로 생성합니다..."
    sudo mkdir -p /var/dobie/nginx
else
    echo "nginx 폴더가 이미 존재합니다."
fi

# cd /var/dobie/nginx
# sudo wget https://raw.githubusercontent.com/eunnseok/dobie-deploy/main/default.conf
# cd ~

# 4. data(json) 폴더 생성
# /var/dobie/data 폴더가 없으면 생성합니다.
if [ ! -d "/var/dobie/data" ]; then
    echo "data 폴더가 없어서 새로 생성합니다..."
    sudo mkdir -p /var/dobie/data
else
    echo "data 폴더가 이미 존재합니다."
fi


# sslLog  파일 생성
# /logfile.log 파일이 없으면 생성합니다.
if [ ! -f "/logfile.log" ]; then
    echo "logfile.log 파일이 없어서 새로 생성합니다..."
    sudo touch /logfile.log
    if [ -f "/logfile.log" ]; then
        echo "로그파일 생성완료"
    else
        echo "로그파일 생성실패"
    fi
else
    echo "logfile.log 파일이 이미 존재합니다."
fi


cd /var/dobie/data
sudo wget https://raw.githubusercontent.com/hyeseon97/Dobie/main/setting/user.json
sudo wget https://raw.githubusercontent.com/hyeseon97/Dobie/main/setting/project.json
sudo wget https://raw.githubusercontent.com/hyeseon97/Dobie/main/setting/refreshToken.json
cd ~

# 5. Docker network 생성
# 'dobie' 네트워크가 이미 존재하는지 확인합니다.
if ! sudo docker network ls | grep -qw dobie; then
    echo "dobie 네트워크가 없어서 새로 생성합니다..."
    sudo docker network create dobie
else
    echo "dobie 네트워크가 이미 존재합니다."
fi



# 6. 파이프 생성
pipe_path="/var/dobie/ssl"

# named pipe가 존재하지 않을 때만 실행
if [ ! -p "$pipe_path" ]; then
  # mkfifo 명령을 실행할 수 있는지 확인하고 권한 부여
  if ! [ -x "$(command -v mkfifo)" ]; then
    echo 'mkfifo 명령을 찾을 수 없습니다. 권한을 부여합니다.'
    sudo chmod +x "$(command -v mkfifo)"
    if [ $? -ne 0 ]; then
      echo 'Error: 권한을 부여할 수 없습니다.'
    fi
  fi
  
  # named pipe를 생성할 디렉토리에 쓰기 권한이 없을 때 권한 부여
  if [ ! -w "$(dirname "$pipe_path")" ]; then
    echo 'named pipe를 생성할 디렉토리에 쓰기 권한이 없습니다. 권한을 부여합니다.'
    sudo chmod +w "$(dirname "$pipe_path")"
    if [ $? -ne 0 ]; then
      echo 'Error: 권한을 부여할 수 없습니다.'
    fi
  fi
  
  # named pipe 생성
  sudo mkfifo "$pipe_path"
  if [ $? -eq 0 ]; then
    echo "Named pipe 생성 완료: $pipe_path"
  else
    echo "Error: Named pipe를 생성할 수 없습니다."
  fi
else
  echo "이미 존재하는 named pipe입니다: $pipe_path"
fi




#7. 서버 재시작 시 파이프 자동연
script_path="/pipe_config/pipe_script.sh"

# /pipe_config 디렉토리가 존재하지 않거나, 해당 디렉토리에 pipe_script.sh 파일이 없을 때에만 작성 및 저장
if [ ! -d "/pipe_config" ] || [ ! -f "$script_path" ]; then
    
    # 스크립트 작성
    echo '#!/bin/bash
    sudo sh -c "
    while true; do
        output=$(eval "$(cat /var/dobie/ssl)")
        echo \"$output\" | sudo tee -a /logfile.log >/dev/null
    done
    " &' > pipe_script.sh

    sudo chmod +x pipe_script.sh


    # 스크립트 저장
    sudo mkdir -p /pipe_config
    sudo mv pipe_script.sh /pipe_config/
    
    if [ -f "/pipe_config/pipe_script.sh" ]; then
        echo "스크립트 작성 및 저장이 완료되었습니다."
    else
        echo "스크립트를 저장하는데 문제가 발생했습니다."
    fi
else
    echo "이미 /pipe_config 디렉토리가 존재하거나 해당 디렉토리에 pipe_script.sh 파일이 존재합니다."
fi




sudo chmod +x /pipe_config/pipe_script.sh


cron_setting="@reboot $script_path"
cron_settings_file="cron_settings"

# named pipe가 존재하고 스크립트 파일이 존재할 때에만 실행
if [ -p "$pipe_path" ] && [ -f "$script_path" ]; then
    # 이미 해당 설정이 존재하는지 확인
    if ! crontab -l | grep -qF "$cron_setting"; then
        # 새로운 설정을 파일에 저장
        echo "$cron_setting" > "$cron_settings_file"

        # 새로운 crontab 파일로 적용
        crontab "$cron_settings_file"
        
        # 임시 파일 제거
        rm "$cron_settings_file"

        # crontab에 설정이 적용되었는지 확인
        if crontab -l | grep -qF "$cron_setting"; then
            echo "새로운 crontab 설정을 추가했습니다: $cron_setting"
        else
            echo "Error: 새로운 crontab 설정을 추가하지 못했습니다: $cron_setting"
        fi
    else
        echo "이미 해당 crontab 설정이 존재합니다: $cron_setting"
    fi
else
    echo "Named pipe와/또는 스크립트 파일이 존재하지 않습니다."
fi


# 8. git 설치 / 소스코드 clone /.env 파일(ip 관련) 생성
sudo apt install git

git clone https://github.com/hyeseon97/Dobie.git

export IP_ADDRESS=$(curl -4 ifconfig.me)
echo "REACT_APP_SERVER=http://$IP_ADDRESS:8010/api" > ./S10P31B101/frontend/.env



# 9. docker-compose.yaml 가져온 후 실행
echo "Dobie의 docker-compose.yml 을 가져옵니다."
wget https://raw.githubusercontent.com/hyeseon97/Dobie/main/setting/docker-compose.yml

if [ $? -eq 0 ]; then
    echo "Docker Compose 파일이 성공적으로 저장되었습니다."
    sudo docker compose -f docker-compose.yml up -d
else
    echo "Docker Compose 파일을 가져오는 데 문제가 발생했습니다."
fi

echo "스크립트 실행이 완료되었습니다."

# 9. 파이프 연결
# named pipe가 존재할 때에만 실행
if [ -p "$pipe_path" ]; then
    sudo sh -c 'while true; do output=$(eval "$(cat /var/dobie/ssl)"); echo "$output" | sudo tee /logfile.log >/dev/null; done' &
fi
