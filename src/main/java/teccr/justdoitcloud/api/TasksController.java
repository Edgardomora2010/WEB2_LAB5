package teccr.justdoitcloud.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import teccr.justdoitcloud.data.Task;
import teccr.justdoitcloud.data.User;
import teccr.justdoitcloud.service.TaskService;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users/{userId}/tasks")
public class TasksController {

    private final TaskService taskService;

    public TasksController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Iterable<Task> getTasksForUser(@PathVariable Long userId) {
        User user = new User();
        user.setId(userId);
        return taskService.getTasksForUser(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task addTaskToUser(@PathVariable Long userId,
                              @RequestBody(required = false) Task task,
                              @RequestParam(name = "autogenerate", required = false) String autogenerate) {
        User user = new User();
        user.setId(userId);

        boolean auto = autogenerate != null && (autogenerate.isEmpty() || autogenerate.equalsIgnoreCase("true"));

        if (auto) {
            // Ignorar el cuerpo y usar el generador para crear la tarea
            return taskService.autogenerateTaskForUser(user);
        }

        // Flujo normal: crear usando el Task provisto en el body
        if (task == null) {
            throw new IllegalArgumentException("Task body is required when autogenerate is not used");
        }
        return taskService.addTaskToUser(user, task);
    }

    /**
     * Observaciones: Se determina, que este método es que mapea el endpoint:
     *
     * /api/v1/users/{userId}/tasks/{id}
     *
     * De forma que devuelve una tarea por medio de su id
     * de tarea, pero en ningún momento se envía como parámetro en id del usuario. Esto quizás
     * por que las tareas a nivel de base de datos si apuntan a un usuario. Por lo tanto es a
     * nivel del servicio (taskService) que debería implementarse una validación sobre si la tarea
     * consultada, corresponde a un usuario, y el usuario existe, etc y devolverse el mensaje indicado
     * por el enunciado.
     *  Por otro lado, en la capa de servicio, no se encuentra una forma, comparar una tarea con un usuario
     *  asociado, sin dejar de enviar el id de usuario para comparar, ya que en el endpoind se está enviando
     *  pero no se está utilizando, por lo tanto se modifica el método para recibir ambos parámetros.
     * @param userId
     * @param id (task)
     * @return
     */
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable Long id, @PathVariable Long userId) {
        // Se crea método en servicio que pueda recibir 2 argumentos explícitos en la consulta al
        // endpoint que plantea el enunciado (taskId, userId)
        Optional<Task> taskOpt = taskService.getValidUserTaskByTaskId(id,userId);
        return taskOpt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Observaciones: Para el caso del patchmapping que se encarga de actualizar las tareas relativas a los
     * usuarios. Se detecta que a diferencia del ejercicio 1, este envía al método del servicio el id de la
     * tarea, y  un objeto de tipo tarea, en cuyos datos internos se puede obtener el id la tarea, y un usuario
     * asociado a la misma. Sin embargo sigue siendo necesario enviar el id del usuario, o un objeto usuario al
     * servicio para establacer una comparación razonable, y que permita evaluar tan correspondencia antes de
     * aprobar cualquier llamado a método de actualización.
     *
     * Por lo antes dicho, en el updatetask también se incluye el usuario, como parámetro en argumentos.
     * A su vez también en el métdo updatataskfields, del servicio, se incluye el parámetro userId, para que en
     * dicho método de actualización se pueda establecer una comparación.
     *
     * También la respuesta a la vista, se maneja como en el ejericio 1, ya que este método no manejaba
     * ResponseEntity como si lo hacía el GetMapping, por lo tanto se incluye mismo funcionamiento. En caso
     * de recibirse un taskOpt válido (tarea actualizada), se envía respuesta http y tarea actualizada, en caso
     * contrario, se envia el forbidden requerido.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task task, @PathVariable Long userId) {

        Optional<Task> taskOpt =
                taskService.updateTaskFields(id, task, userId);
        return taskOpt
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /**
     * Observaciones: Para el caso de enpoint, para borrado, se asume lo mismo que para los casos anteriores
     * se agrega el parámetro extra de id usuario, para poder establecer comparaciones, en el servicio y
     * determinar si una tarea específica pertenece a un usuario específico, antes de cualquier intento de
     * borrado.
     * Se cambia método void , a función para poner retornar error como en métodos anteriores GET, PATCH
     * mapping.
     * @param id
     * @param userId
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long id,
            @PathVariable Long userId) {

        boolean deleted = taskService.deleteTaskById(id, userId);

        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
