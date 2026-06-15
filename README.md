# Dupe Radar Addon for Autism Client

An addon for the [Autism Client](https://github.com/AutismDevelopment/Autism-Client) that scans server plugins against known dupe databases. **(Basically it shows you possible dupes in the server)**
*Note: This is already added to main client but you can still use this addon without any problem.*

**Disclaimer:** Use this mod at your own risk. The maintainers are not responsible for any bans or other consequences resulting from the use of this software. 

---

## Overview
Dupe Radar is a client-side tool designed to identify potential vulnerabilities by cross-referencing a server's active plugins with known exploit databases.

## How it works
This addon detects the plugins running on the server and checks them against known dupe databases (currently supporting [DupeDB](https://dupedb.net/)). If any matches are found, it lists them for you, allowing you to identify potentially compromised environments.

## DupeDB
This project utilizes the [DupeDB](https://dupedb.net/) API to fetch and verify plugin/server data. All results are based on the information provided by this external database.

---

### Acknowledgments
*   Built for [Autism Client](https://github.com/AutismDevelopment/Autism-Client)
*   Data provided by [DupeDB](https://dupedb.net/)
