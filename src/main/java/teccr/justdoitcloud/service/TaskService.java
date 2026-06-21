package teccr.justdoitcloud.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import teccr.justdoitcloud.data.Task;
import teccr.justdoitcloud.data.User;
import teccr.justdoitcloud.repository.TaskRepository;
import teccr.justdoitcloud.repository.UserRepository;
import teccr.justdoitcloud.service.external.taskgenerator.TaskGenerator;
import teccr.justdoitcloud.service.internal.taskarchiver.TaskArchiver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskGenerator taskGenerator;
    private final UserRepository userRepository;
    private final TaskArchiver taskArchiver;
    private final UserService userService;

    // Constructor de clase con inyección de dependencias
    public TaskService(TaskRepository taskRepository,
                       TaskGenerator taskGenerator,
                       UserRepository userRepository,
                       TaskArchiver taskArchiver, UserService userService) {
        this.taskRepository = taskRepository;
        this.taskGenerator = taskGenerator;
        this.userRepository = userRepository;
        this.taskArchiver = taskArchiver;
        this.userService = userService;
    }

    // Devuelve lista de tareas por usuario
    public List<Task> getTasksForUser(User user) {
        return taskRepository.findByUserId(user.getId());
    }

    public Task addTaskToUser(User user, Task task) {
        task.setUserId(user.getId());
        task.setCreatedAt(LocalDateTime.now());
        Task taskCreated = taskRepository.save(task);

        Optional<User> maybeUser = userRepository.findById(user.getId());
        if (maybeUser.isPresent()) {
            taskArchiver.archiveTask("tasks-new", maybeUser.get(), taskCreated);
        }

        return taskCreated;
    }

    public Task autogenerateTaskForUser(User user) {
        // Pendiente: archivar la tarea en categoria "tasks-new" despues de creada

        Task task = taskGenerator.generateTask();
        if (task == null) {
            throw new RuntimeException("No se pudo generar la tarea automáticamente");
        }

        task.setUserId(user.getId());
        task.setCreatedAt(LocalDateTime.now());

        return taskRepository.save(task);
    }

    // obtiene tarea por id de tarea
    public Optional<Task> getTaskById(Long id) {

        if (id == null || id < 0) {
            return Optional.empty();
        }
        return taskRepository.findById(id);
    }

    /**
     * Internamente debe consultarse la tarea, pero si la misma no pertenece al usuario
     * identificado por {userId} debe devolverse una respuesta con el cuerpo vacío y el
     * código 404 (Not Found).
     */
    public Optional<Task> getValidUserTaskByTaskId(Long taskId, Long userId) {

        // Se obtiene datos de tarea a partir del id de tarea, utilizando parámetro
        // consultado en el endpoint (taskId)
        Optional<Task> task = getTaskById(taskId);

        // Si la tarea no existe o no es vacía
        if (task.isEmpty()) {
            return Optional.empty();
        }

        // se obtiene id de usuario, obtenido de la tarea
        long taskUserId = task.get().getUserId();

        // Se compara el id de usuario obtenido desde los datos internos del objeto tarea, contra
        // el id de usuario recibido en argumentos (userId), que también es otro parámetro utilizado
        // (que se agregó, ya se recibía en consulta al endpoint, pero no se utilizaba en método del
        // endpoint), si estos coinciden entonces la devolución de datos de la tarea es válida, si no
        // se debe devolver en el error 404 (no se encontraron datos, dado que no existe un usuario
        // con ese id, para una tarea con ese id recibido en la consulta al endpoint).
        if (userId == taskUserId) {
            // si el id de usuario en argumentos, corresponde al id de usuario, en los datos internos
            // del objeto tarea, devuelve la tarea
            return task;
        }

        return Optional.empty();
    }

    /**
     * Observaciones se incluyo el parámetro userId, acá hay una complicación ligera, y es que
     * este método pese a ser de update devuelve una tarea actualizada, por lo que lo mas simple fue
     * reutilizar el método del ejercicio 1 que ya evaluaba la correspondencia entre 1 user id, y un
     * user id dentro de un objeto tarea, de forma que solo se devuelva cuando, el método del ejercicio
     * se cumple, sin embargo se pensó en devolver boolean (true, or false), pero en controller recibe la
     * tarea por lo tanto, lo más facil para poder manejar desde el controller el codigo de error que plantea
     * el enunciado es que si no hay correspondencia, se devuelva también un optional vacío. Pero se trató
     * de realizar cambios mínimos en el servicio para no afectar luego otros funcionamientos, o modificar mas
     * código dependiente.
     * @param id
     * @param updatedTask
     * @param userId
     * @return
     */
    public Optional<Task> updateTaskFields(Long id, Task updatedTask, Long userId) {

        // Se aprovecha método realizado en ejercicio 1, y que ya devuelve un objeto funcional, si
        // existe una correspondencia entre el id user y el id user dentro del objeto task
        Optional<Task> task = getValidUserTaskByTaskId(id, userId);

        // Si la tarea existe o no es vacía
        if (task.isPresent()) {

            return taskRepository.findById(id)

                    .map(existingTask -> {
                        if (updatedTask.getDescription() != null
                                && !updatedTask.getDescription().trim().isEmpty()) {
                            existingTask.setDescription(updatedTask.getDescription().trim());
                        }

                        if (updatedTask.getStatus() != null) {
                            existingTask.setStatus(updatedTask.getStatus());
                        }

                        return taskRepository.save(existingTask);
                    });
        }

        return Optional.empty();
    }

    // Se incluye párametro de usuario en argumentos de método de borrado para poder evaluar  la
    // correspondencia entre tareas y usuario, y borrado seguro.
    public boolean deleteTaskById(Long id, Long userId)
    {
        // Se comenta líenea original en método de borrado del servicio
        //Optional<Task> maybeTask = taskRepository.findById(id);

        // Se cambia linea original anterior, por versión que realiza comprobación entre tareas y
        // usuarios asociados a tareas
        // Se aprovecha método realizado en ejercicio 1, y que ya devuelve un objeto funcional, si
        // existe una correspondencia entre el id user y el id user dentro del objeto task

        // Este método a diferencia del de búsqueda, y actualización es tipo void, por lo que no
        // devuelve en realidad una respuesta de algo fue borrado, mas allá de un mensaje, pero no
        // un task o un <optional> de task vacío, por tanto resulta prudente que devuelva un estado
        // falso o verdadero si algo se realiza o no.

        Optional<Task> maybeTask = getValidUserTaskByTaskId(id, userId);

        if (maybeTask.isEmpty()) {
            return false;
        }

        Task task = maybeTask.get();

        Optional<User> maybeUser = userRepository.findById(task.getUserId());

        maybeUser.ifPresent(user -> {
            try {

                // se desactiva servicio taskarchiver de otro proyecto externo
                // taskArchiver.archiveTask("tasks-deleted", user, task);

            } catch (Exception ignored) {

                // Resulta conveniente dejar log de excepciones por lo que deja igual.
                log.error("Error archiving task with id {} for user id {}: {}",
                        task.getId(),
                        user.getId(),
                        ignored.getMessage());
            }
        });

         taskRepository.deleteById(id);

        return true;
    }

}
