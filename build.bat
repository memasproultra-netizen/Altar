@echo off
rem Собирает мод. Нужна Java 21 (JDK). Готовый файл: build\libs\altarmod-1.0.0.jar
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo Сборка не удалась. Пришли текст ошибки выше.
) else (
  echo.
  echo Готово! Файл мода: build\libs\altarmod-1.0.0.jar
)
pause
