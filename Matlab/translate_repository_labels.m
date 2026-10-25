function translate_repository_labels
% Translate saved model labels without rebuilding the user's models.
rootDir = fileparts(mfilename('fullpath'));
folder = fullfile(rootDir, 'simulink', 'gs_response');
names = {'gs_pid_closed_loop', 'gs_pid_simple', 'gs_step_response'};
labels = {
    'Control al workspace', 'Control workspace output';
    'Control PI', 'PI controller';
    'Derivada filtrada', 'Filtered derivative';
    'Sumar PID', 'PID sum';
    'Referencia y salida', 'Reference and output';
    'Scope respuesta PID', 'PID response scope';
    'Scope respuesta', 'Response scope';
    'Salida al workspace', 'Workspace output';
    'Mux entrada y salida', 'Input and output mux';
    'Entrada escalon', 'Step input';
    'Salida y(t)', 'Output y(t)';
    'Planta Gs', 'Plant Gs';
    'Referencia', 'Reference';
    'Escalon', 'Step';
    'Descripcion', 'Description';
    'PID con realimentacion unitaria', 'PID with unity feedback';
    'Derivada sobre la medicion: sin golpe derivativo', 'Derivative on measurement: no derivative kick';
    'Respuesta esperada: Ts aprox. 0.18 s, Mp aprox. 5.1 %', 'Expected response: Ts approx. 0.18 s, Mp approx. 5.1 %';
    'Entrada: escalon unitario   |   Tiempo: 5 s.', 'Input: unit step   |   Time: 5 s.';
    'El Scope compara la entrada r(t) con la salida y(t).', 'The scope compares input r(t) with output y(t).';
    };
for k = 1:numel(names)
    model = names{k};
    file = fullfile(folder, [model '.slx']);
    load_system(file);
    cleanup = onCleanup(@() close_system(model, 0));
    blocks = find_system(model, 'FindAll', 'on', 'Type', 'Block');
    for b = blocks(:)'
        old = get_param(b, 'Name');
        new = translateText(old, labels);
        if ~strcmp(old, new)
            set_param(b, 'Name', new);
        end
    end
    annotations = find_system(model, 'FindAll', 'on', 'Type', 'annotation');
    for a = annotations(:)'
        object = get_param(a, 'Object');
        object.Text = translateText(object.Text, labels);
    end
    save_system(model, file);
    % Validate that the translated model can be reloaded and updated.
    close_system(model, 0);
    load_system(file);
    set_param(model, 'SimulationCommand', 'update');
    print(['-s' model], '-dpng', '-r160', ...
        fullfile(folder, [model '_model.png']));
    clear cleanup
    fprintf('Translated and verified: %s\n', file);
end
renderResponsePlots(folder);
end

function result = translateText(text, labels)
result = text;
for k = 1:size(labels, 1)
    result = strrep(result, labels{k, 1}, labels{k, 2});
end
end

function renderResponsePlots(folder)
% Replot numerical data rather than modifying raster chart labels.
t = (0:0.005:5)';
y = step(tf(144, [1 7.2 144]), t);
fig = figure('Visible', 'off', 'Color', 'white');
plot(t, ones(size(t)), '--', 'LineWidth', 1.2); hold on;
plot(t, y, 'LineWidth', 1.8);
grid on; xlabel('Time (s)'); ylabel('Amplitude');
title('Unit-step response of G(s)');
legend('Input r(t)', 'Output y(t)', 'Location', 'best');
xlim([0 5]);
exportgraphics(fig, fullfile(folder, 'gs_step_response.png'), 'Resolution', 160);
close(fig);

model = 'gs_pid_closed_loop';
load_system(fullfile(folder, [model '.slx']));
cleanup = onCleanup(@() close_system(model, 0));
output = sim(model, 'ReturnWorkspaceOutputs', 'on');
y = output.get('y_pid');
u = output.get('u_pid');
fig = figure('Visible', 'off', 'Color', 'white');
subplot(2, 1, 1);
plot(y.time, double(y.time >= 0.1), '--', 'LineWidth', 1.2); hold on;
plot(y.time, y.signals.values, 'LineWidth', 1.8);
grid on; ylabel('Amplitude'); title('Closed-loop PID response');
legend('Reference', 'Output', 'Location', 'best');
subplot(2, 1, 2);
plot(u.time, u.signals.values, 'LineWidth', 1.8);
grid on; xlabel('Time (s)'); ylabel('u(t)'); title('PID control signal');
exportgraphics(fig, fullfile(folder, 'gs_pid_response.png'), 'Resolution', 160);
close(fig);
clear cleanup
fprintf('English response plots generated.\n');
end
