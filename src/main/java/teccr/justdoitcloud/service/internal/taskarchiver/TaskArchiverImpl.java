package teccr.justdoitcloud.service.internal.taskarchiver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import teccr.justdoitcloud.data.Task;
import teccr.justdoitcloud.data.User;

@Slf4j
@Service
public class TaskArchiverImpl implements TaskArchiver {

    private final RestTemplate restTemplate;
    private final String archiverBaseUrl;

    public TaskArchiverImpl(@Value("${internal.archiverBaseUrl}") String archiverBaseUrl,
                            RestTemplate restTemplate) {
        this.archiverBaseUrl = archiverBaseUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public void archiveTask(User user, Task task) {
        if (user == null || task == null) {
            return;
        }

        String contentBody =  user.getName() + " - " + task.getDescription() + " - " + task.getStatus();

        ArchiveRequestDto archiveRequest = new ArchiveRequestDto();
        archiveRequest.setCategory("tasks");
        archiveRequest.setId(String.valueOf(task.getId()));
        archiveRequest.setContent(contentBody);


        restTemplate.postForObject(archiverBaseUrl, archiveRequest, ArchiveRequestDto.class);
        log.info("Externally archived task for user {}: {}", user.getName(), archiveRequest.toString());

    }
}
