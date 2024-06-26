import axios from "axios";
const processUrl = process.env.REACT_APP_SERVER + "/containercheck";

//프로젝트 실행 상태 조회
export async function checkProceeding(projectId) {
  try {
    const response = await axios.get(processUrl + "/proceeding", {
      params: { projectId },
    });

    return response;
  } catch (error) {
    throw error;
  }
}
