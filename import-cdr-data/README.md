задача:

импорт записей из текстового файла CDR (call detail record) в базу данных
пример файла и утилиту для генерации можно посмотреть тут https://github.com/deshpandetanmay/cdr-data-generator

известно, что при большом размере файла основное время ожидания приходится на ответ от БД при сохранении одной записи (commit)
известно, что формат файла может измениться (без изменения набора полей)

 
необходимо реализовать консольную программу для импорта файла в таблицу в БД.
требования:
- добиться максимально быстрого импорта
- использовать 2 разных потока для чтения из файла и записи в БД

БД любая, можно H2

решение:
реализовал шаблон producer-consumer. 
продюсер - поток, читающий файл cdr.processing.Producer.  
Producer использует абстракцию формата файла cdr.parse.AbstractDataParser. 
То есть, компонент, который читает файл в определённом формате и представляет записи в виде  DTO модели cdr.domain.CallDataRecord. За это отвечает класс cdr.parse.CDRDataParser.
Consumer - это поток cdr.processing.Consumer, пишущий в базу DTO (cdr.domain.CallDataRecord), произведённые в producer'е. 
Т.к. самая большая нагрузка идёт на коммит, то запись пачками (batch) выглядит логичной. Реализация batch реализована здесь cdr.repository.impl.CDRRepositoryImpl#addCDR

Чтение записей файла производится по байтово, т.е. весь файл не грузится в память. Реализация - cdr.processing.Producer#parseCDR
final LineIterator it = FileUtils.lineIterator(file, "UTF-8");

парсинг файла или одной строки файла, прячущийся за интерфейсом/абстрактным классом cdr.parse.AbstractDataParser. Соответственно, реализацию (cdr.parse.CDRDataParser), которого можно безболезненно подменять при смене формата файла.