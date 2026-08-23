name: Bug report
about: Report a problem with the mod
labels: bug
body:
  - type: textarea
    id: description
    attributes:
      label: What happened?
      description: A clear description of the bug. If applicable, attach the crash report or latest.log.
    validations:
      required: true
  - type: input
    id: versions
    attributes:
      label: Versions
      description: 'Example: Minecraft 1.21.1, NeoForge 21.1.248, Create 6.0.11, Colored Connections 0.1.0'
    validations:
      required: true
  - type: textarea
    id: reproduce
    attributes:
      label: Steps to reproduce
      placeholder: |
        1. ...
        2. ...
