import csv

# Path to the input text file and output CSV file
input_file = 'path_to_your_input_file.txt'
output_file = 'output.csv'

with open(input_file, 'r') as infile, open(output_file, 'w', newline='') as outfile:
    csv_writer = csv.writer(outfile)
    
    # Write the header
    csv_writer.writerow(['smiles'])

    # Read each line from the input file
    for line in infile:
        # Split the line by space and take the first part (SMILES data)
        smiles_data = line.split()[0]
        # Write the SMILES data to the CSV file
        csv_writer.writerow([smiles_data])

print("Conversion complete. The output is saved in", output_file)
