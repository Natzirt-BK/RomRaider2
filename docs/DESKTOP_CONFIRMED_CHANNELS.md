# Desktop vehicle-confirmed SSM channels

Included in the 1.1.11 RC1 desktop Logger runtime.

Before SSM identification, the selectable ECU channel list is empty. The loaded
definition and profile remain available internally for setup and recovery;
external sensor channels are separate.

After identification, standard parameters and switches must have a supported
SSM capability flag. ECU-specific channels must match the identified ECU.
Calculated channels require confirmed dependencies. The selected ECU/TCU target
also filters the catalog; channels from the other module are excluded.
Runtime-discovered DimeMod channels are added after discovery.

The filtered runtime catalog supplies channel selection and polling, not just
the displayed labels. Generic addresses without support evidence are excluded.
Definition files and named profiles are not rewritten by catalog filtering.
Reload a saved profile after identification/discovery to review its available
selections; unsupported entries are not silently re-enabled.

MUT-II and external sensor catalogs are unchanged. The retained legacy Swing
logger keeps its original filtering behavior. Capability flags and exact ECU
mappings do not independently validate definition scaling or sensor fitment.
