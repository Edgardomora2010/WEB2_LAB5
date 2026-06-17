package teccr.justdoitcloud.service.internal.taskarchiver;


import lombok.Data;

@Data
public class ArchiveRequestDto {
    private String category;
    private String id;
    private String content;
}
