"""Inspect an explicitly shared, already running MATLAB session."""
import os
import sys

root = "C:/Program Files/MATLAB/R2026b"
os.environ["PATH"] = root + "/bin/win64;" + os.environ["PATH"]
dll_directory = os.add_dll_directory(root + "/bin/win64")
sys.path[:0] = [
    root + "/extern/engines/python/dist",
    root + "/extern/engines/python/dist/matlab/engine/win64",
    root + "/extern/bin/win64",
]
import matlab.engine

sessions = matlab.engine.find_matlab()
print("SHARED_SESSIONS:", sessions, flush=True)
if "CodexMATLAB" not in sessions:
    raise SystemExit("La sesión CodexMATLAB aún no está compartida.")
engine = matlab.engine.connect_matlab("CodexMATLAB")
engine.eval("disp(version); disp(pwd); disp(find_system('type','block_diagram'));", nargout=0)
engine.eval(
    "disp(which('GantryRobotSubsystem')); "
    "disp(which('XYZGantryRobotParameters')); "
    "whos Floor PrintBed PrintHead; "
    "if bdIsLoaded('XYZCartesianRobot3DPrinting'), "
    "disp(get_param('XYZCartesianRobot3DPrinting','FileName')); "
    "disp(get_param('XYZCartesianRobot3DPrinting','Dirty')); "
    "disp(get_param('XYZCartesianRobot3DPrinting','InitFcn')); "
    "disp(get_param('XYZCartesianRobot3DPrinting','PreLoadFcn')); end",
    nargout=0,
)
