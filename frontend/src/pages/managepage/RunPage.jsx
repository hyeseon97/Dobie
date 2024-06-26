import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import NavTop from "../../components/common/NavTop";
import NavLeft from "../../components/common/NavLeft";

import Swal from "sweetalert2";
import toast from "react-hot-toast";
import styles from "./RunPage.module.css";
import run from "../../assets/run.png";
import stop from "../../assets/stop.png";
import edit from "../../assets/editIcon.png";
import remove from "../../assets/deleteIcon.png";
import setting from "../../assets/settingIcon.png";
import upload from "../../assets/uploadIcon.png";
import document from "../../assets/documentIcon.png";
import restart from "../../assets/restart.png";
import build from "../../assets/settings.png";

import {
  buildProject,
  deleteProject,
  startProject,
  stopProject,
} from "../../api/Project";
import { getNginxConf } from "../../api/ngixn";
import { getDockerCompose } from "../../api/Docker";
import { checkProceeding } from "../../api/CheckProcess";

import useProjectStore from "../../stores/projectStore";
import useModalStore from "../../stores/modalStore";
import RunProjectList from "../../components/manage/RunProjectList";
import Modal from "../../components/modal/Modal";
import LoadingModal from "../../components/modal/LoadingModal";
import NewMadal from "../../components/modal/NewModal";

export default function RunPage() {
  const [content, setContent] = useState("");
  const { setAction } = useModalStore();
  const { modalOpen, setModalOpen } = useModalStore();
  const { setFileType } = useModalStore();
  const { checkProceed, setCheckProceed } = useProjectStore();
  const { loadingModal, setLoadingModal } = useModalStore();
  const { selectedProject, setUpdatedProject } = useProjectStore();
  const [isLoading, setIsLoading] = useState(true);

  const navigate = useNavigate();

  useEffect(() => {
    handleCheckProceding();
    setLoadingModal(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  //실행상태 조회
  const handleCheckProceding = async () => {
    try {
      const response = await checkProceeding(selectedProject.projectId);
      if (response.data.status === "SUCCESS") {
        setCheckProceed(response.data.data);
        setIsLoading(false);
      } else {
        setCheckProceed({ allRunning: "null" });
        toast.error(`프로젝트 실행상태를 불러올수 없습니다.`, {
          position: "top-center",
        });
      }
    } catch (error) {
      console.error("컨테이너 실행 확인 에러: ", error);
    }
  };

  //프로젝트 삭제
  const handleDelete = async (projectId) => {
    // SweetAlert로 사용자에게 확인 받기
    Swal.fire({
      title: "프로젝트 삭제",
      text: "이 작업은 되돌릴 수 없습니다!",
      icon: "warning",
      showCancelButton: true,
      confirmButtonColor: "#4FC153",
      cancelButtonColor: "#FF5370",
      confirmButtonText: "예, 삭제합니다!",
      cancelButtonText: "아니요, 취소합니다!",
    }).then((result) => {
      if (result.isConfirmed) {
        // 사용자가 '예'를 클릭했을 때만 삭제 처리 진행
        deleteProject(projectId)
          .then((response) => {
            if (response.data.status === "SUCCESS") {
              navigate("/main");
              Swal.fire({
                title: "삭제 완료!",
                text: "프로젝트가 성공적으로 삭제되었습니다.",
                icon: "success",
                confirmButtonColor: "#4FC153",
                showCancelButton: false,
                confirmButtonText: "OK",
              });
            } else {
              Swal.fire(
                "삭제 실패!",
                "프로젝트를 삭제할 수 없습니다.",
                "error"
              );
            }
          })
          .catch((error) => {
            console.error("프로젝트 삭제 실패: ", error);
            Swal.fire(
              "오류 발생!",
              "프로젝트 삭제 중 문제가 발생했습니다.",
              "error"
            );
          });
      }
    });
  };

  //nginx config 조회
  const handleOpenNginxModal = async (projectId) => {
    try {
      const response = await getNginxConf(projectId);
      if (response.data.status === "SUCCESS") {
        await setFileType("nginx");
        setModalOpen(true);
        setContent(response.data.data);
      } else {
        toast.error("nginx config 파일 조회 실패", {
          position: "top-center",
        });
      }
    } catch (error) {
      console.log("nginx config 조회 실패: " + error);
    }
  };

  //도커 컴포즈 조회
  const handleDockerComposeModal = async (projectId) => {
    try {
      const response = await getDockerCompose(projectId);
      if (response.status === 200) {
        setModalOpen(true);
        setFileType("dockerCompose");
        setContent(response.data);
      } else {
        toast.error("도커 컴포즈 파일 조회 실패", {
          position: "top-center",
        });
      }
    } catch (error) {
      console.log("docker compose 조회 실패: " + error);
    }
  };

  //전체 프로젝트 중지
  const handleProjectStop = async (projectId) => {
    try {
      if (checkProceed.allRunning === "Run") {
        setAction("stop");
        setLoadingModal(true);
        await stopProject(projectId).then(() => setLoadingModal(false));
        setCheckProceed({ allRunning: "null" });
        toast.success(`성공적으로 중지되었습니다. `, {
          position: "top-center",
        });
      } else {
        toast.error(`이미 중지된 프로젝트 입니다. `, {
          position: "top-center",
        });
      }
    } catch (error) {
      console.log("프로젝트 정지 실패: " + error);
    }
  };

  //전체 프로젝트 실행
  const handleProjectStart = async (projectId) => {
    try {
      setAction("run");
      setLoadingModal(true);
      await startProject(projectId).then(() => setLoadingModal(false));
      setCheckProceed({ allRunning: "Run" });
      window.location.replace("/manage");
    } catch (error) {
      setLoadingModal(false);
      toast.error(`프로젝트 등록 후 빌드가 진행되어야 합니다. `, {
        position: "top-center",
      });
    }
  };

  // 프로젝트 빌드
  const handleProjectBuild = async (projectId) => {
    try {
      setAction("build");
      setLoadingModal(true);
      const response = await buildProject(projectId);
      if (response.data.status === "SUCCESS") {
        setLoadingModal(false);
        toast.success("빌드파일이 성공적으로 생성되었습니다.");
      }
    } catch (error) {
      console.log("에러발생", error);
    }
  };

  const handleUpdateProject = () => {
    setUpdatedProject({ ...selectedProject });
    navigate("/update/project");
  };

  return (
    <>
      {isLoading ? (
        <>
          <NewMadal />
          <NavTop />
          <NavLeft num={1} />
        </>
      ) : (
        <>
          <NavTop />
          <NavLeft num={1} />
          <div className={styles.page}>
            <div className={styles.top}>
              <div>
                <div className={styles.text}>프로젝트</div>
                <div className={styles.projectName}>
                  {selectedProject.projectName}
                </div>
              </div>
              <div className={styles.buttons}>
                <div
                  className={styles.webhook}
                  onClick={() => navigate("/manage/webhook")}
                >
                  Webhook 설정{" "}
                  <img
                    src={setting}
                    alt=""
                    decoding="async"
                    className={styles.btnIcon}
                  />
                </div>
                <div
                  className={styles.webhook}
                  onClick={() => navigate("/manage/file")}
                >
                  환경설정 파일 추가/삭제{" "}
                  <img
                    src={upload}
                    alt=""
                    decoding="async"
                    className={styles.btnIcon}
                  />
                </div>
                <div className={styles.edit} onClick={handleUpdateProject}>
                  수정{" "}
                  <img
                    src={edit}
                    alt=""
                    decoding="async"
                    className={styles.btnIcon}
                  />
                </div>
                <div
                  className={styles.remove}
                  onClick={() => handleDelete(selectedProject.projectId)}
                >
                  삭제{" "}
                  <img
                    src={remove}
                    alt=""
                    decoding="async"
                    className={styles.btnIcon}
                  />
                </div>
              </div>
            </div>
            <div className={styles.mid}>
              <div>
                <div className={styles.text}>프로젝트 전체 실행</div>
                <div className={styles.runButton}>
                  <div>
                    <img
                      src={build}
                      className={styles.runButtonIcon}
                      alt=""
                      onClick={() =>
                        handleProjectBuild(selectedProject.projectId)
                      }
                    />
                    <img
                      src={checkProceed.allRunning === "Run" ? restart : run}
                      className={styles.runButtonIcon}
                      alt=""
                      onClick={() =>
                        handleProjectStart(selectedProject.projectId)
                      }
                    />
                    <img
                      src={stop}
                      className={styles.stopButtonIcon}
                      alt=""
                      onClick={() =>
                        handleProjectStop(selectedProject.projectId)
                      }
                    />
                  </div>
                </div>
              </div>
              <div className={styles.buttons}>
                <div
                  className={styles.fileButton}
                  onClick={() =>
                    handleOpenNginxModal(selectedProject.projectId)
                  }
                >
                  nginx.config 파일 조회{" "}
                  <img
                    src={document}
                    alt=""
                    decoding="async"
                    className={styles.btnIcon}
                  />
                </div>
                <div
                  className={styles.fileButton}
                  onClick={() =>
                    handleDockerComposeModal(selectedProject.projectId)
                  }
                >
                  docker-compose.yml 파일 조회{" "}
                  <img
                    src={document}
                    alt=""
                    decoding="async"
                    className={styles.btnIcon}
                  />
                </div>
              </div>
            </div>
            {checkProceed.allRunning === "Run" && (
              <RunProjectList setContent={setContent} />
            )}
          </div>
          {loadingModal && <LoadingModal />}
          {modalOpen && <Modal content={content} />}
        </>
      )}
    </>
  );
}
