# v2.1.1
- Последнии актуальные изменения.

# v2.1
- Добавлена стратегия `REQUEST_EXCLUDE_CACHE`, исключающая получения данных из кэша.

# v2.0
- Интерфейсы api теперь имеют поддержку rx. `RequestCallback` поддерживается, но помечена устаревшей
- Ядро полностью переписано с использованием rx
- Резул****ьтат выполнения `InvocationBlock`'а теперь выставляется не в задачу, а в promise на эту задачу. Сама задача неизменяема
- В CoreTask больше нет методов `sleep` и `wakeUp`. Каждый блок теперь работает с задачами асинхронно и обязан вызвать один из методов `Promise` для сообщения результата выполнения
- В `InvocationBlock` теперь вызывается только один из методов `onEntity` или `onQueueConsumed` в зависимости от выбранного алгоритма выполнения блока
- Из интерфейса `IConverterExecutor` удалён метод `executePostSequence`. Возможность вручную собрать связку теперь окончательно убрана. `RequestMerger` также помечен как устаревший
- Задачи теперь поддерживают признак завершения

# v1.1

- Ядро пакета и `RequestExecutor` больше не привязаны к стандарту RPC
- Текущий `ExecutorWrapper` теперь предназначен только для RPC, для других стандартов требуется описать свой и свои `PostParams`
- Максимальное количество запросов в batch'е теперь устанавливается для каждой сущности сервер апи
- С помощью аннотации `@NoBatch` можно указать, чтоб запрос не попадал в связки